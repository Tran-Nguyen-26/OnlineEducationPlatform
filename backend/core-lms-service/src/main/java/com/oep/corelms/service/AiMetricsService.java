package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiMetricsService {
    private final JdbcTemplate jdbc;
    private final ExamPanicCircuitBreaker examPanicCircuitBreaker;
    @Value("${oep.ai.cost.input-usd-per-million:0}")
    private BigDecimal inputCostPerMillion;
    @Value("${oep.ai.cost.output-usd-per-million:0}")
    private BigDecimal outputCostPerMillion;

    public record DailyMetric(LocalDate date, long questions, long promptTokens, long completionTokens,
                              double averageLatencyMs, double refusalRate, BigDecimal estimatedCostUsd) {}
    public record CourseMetric(String courseId, String courseTitle, long questions, long promptTokens,
                               long completionTokens, double averageLatencyMs, double refusalRate,
                               BigDecimal estimatedCostUsd) {}
    public record AiMetrics(LocalDate from, LocalDate to, long questions, long promptTokens,
                            long completionTokens, double averageLatencyMs, double refusalRate,
                            List<DailyMetric> daily, List<CourseMetric> courses,
                            long mistakeCacheHits, long mistakeCacheMisses, double mistakeCacheHitRate,
                            BigDecimal estimatedCostUsd, boolean costConfigured) {}

    @Transactional(readOnly = true)
    public AiMetrics get(LocalDate from, LocalDate to) {
        examPanicCircuitBreaker.requireAncillaryServicesAvailable();
        if (from == null || to == null || from.isAfter(to) || from.isBefore(to.minusYears(2))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Khoảng ngày thống kê AI không hợp lệ (tối đa 2 năm)");
        }
        Totals totals = jdbc.queryForObject("""
                select count(*) as questions,
                       coalesce(sum(prompt_tokens), 0) as prompt_tokens,
                       coalesce(sum(completion_tokens), 0) as completion_tokens,
                       coalesce(avg(latency_ms), 0) as average_latency,
                       coalesce(sum(case when is_refused then 1 else 0 end), 0) as refused
                from ai_usage_events
                where created_at >= (?::date::timestamp at time zone 'Asia/Ho_Chi_Minh')
                  and created_at < ((?::date + 1)::timestamp at time zone 'Asia/Ho_Chi_Minh')
                """, (rs, row) -> new Totals(rs.getLong("questions"), rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"), rs.getDouble("average_latency"), rs.getLong("refused")),
                Date.valueOf(from), Date.valueOf(to));

        List<DailyMetric> daily = jdbc.query("""
                select (created_at at time zone 'Asia/Ho_Chi_Minh')::date as metric_date,
                       count(*) as questions,
                       coalesce(sum(prompt_tokens), 0) as prompt_tokens,
                       coalesce(sum(completion_tokens), 0) as completion_tokens,
                       coalesce(avg(latency_ms), 0) as average_latency,
                       coalesce(sum(case when is_refused then 1 else 0 end), 0) as refused
                from ai_usage_events
                where created_at >= (?::date::timestamp at time zone 'Asia/Ho_Chi_Minh')
                  and created_at < ((?::date + 1)::timestamp at time zone 'Asia/Ho_Chi_Minh')
                group by metric_date order by metric_date
                """, (rs, row) -> {
            long questions = rs.getLong("questions");
            return new DailyMetric(rs.getDate("metric_date").toLocalDate(), questions,
                    rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"), rs.getDouble("average_latency"),
                    rate(rs.getLong("refused"), questions), cost(rs.getLong("prompt_tokens"), rs.getLong("completion_tokens")));
        }, Date.valueOf(from), Date.valueOf(to));

        List<CourseMetric> courses = jdbc.query("""
                select c.id::text as course_id, c.title as course_title, count(*) as questions,
                       coalesce(sum(m.prompt_tokens), 0) as prompt_tokens,
                       coalesce(sum(m.completion_tokens), 0) as completion_tokens,
                       coalesce(avg(m.latency_ms), 0) as average_latency,
                       coalesce(sum(case when m.is_refused then 1 else 0 end), 0) as refused
                from ai_usage_events m
                join courses c on c.id = m.course_id
                where m.course_id is not null
                  and m.created_at >= (?::date::timestamp at time zone 'Asia/Ho_Chi_Minh')
                  and m.created_at < ((?::date + 1)::timestamp at time zone 'Asia/Ho_Chi_Minh')
                group by c.id, c.title order by questions desc, c.title
                """, (rs, row) -> {
            long questions = rs.getLong("questions");
            return new CourseMetric(rs.getString("course_id"), rs.getString("course_title"), questions,
                    rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"), rs.getDouble("average_latency"),
                    rate(rs.getLong("refused"), questions), cost(rs.getLong("prompt_tokens"), rs.getLong("completion_tokens")));
        }, Date.valueOf(from), Date.valueOf(to));

        if (totals == null) totals = new Totals(0, 0, 0, 0, 0);
        CacheTotals cache = jdbc.queryForObject("""
                select count(*) filter (where cache_hit) as hits,
                       count(*) filter (where not cache_hit) as misses
                from ai_mistake_cache_events
                where created_at >= (?::date::timestamp at time zone 'Asia/Ho_Chi_Minh')
                  and created_at < ((?::date + 1)::timestamp at time zone 'Asia/Ho_Chi_Minh')
                """, (rs, row) -> new CacheTotals(rs.getLong("hits"), rs.getLong("misses")),
                Date.valueOf(from), Date.valueOf(to));
        return new AiMetrics(from, to, totals.questions(), totals.promptTokens(), totals.completionTokens(),
                totals.averageLatencyMs(), rate(totals.refused(), totals.questions()), daily, courses,
                cache.hits(), cache.misses(), rate(cache.hits(), cache.hits() + cache.misses()),
                cost(totals.promptTokens(), totals.completionTokens()),
                inputCostPerMillion.signum() > 0 || outputCostPerMillion.signum() > 0);
    }

    private BigDecimal cost(long promptTokens, long completionTokens) {
        return BigDecimal.valueOf(promptTokens).multiply(inputCostPerMillion.max(BigDecimal.ZERO))
                .add(BigDecimal.valueOf(completionTokens).multiply(outputCostPerMillion.max(BigDecimal.ZERO)))
                .divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP);
    }

    private double rate(long refused, long total) {
        return total == 0 ? 0 : BigDecimal.valueOf(refused).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private record Totals(long questions, long promptTokens, long completionTokens, double averageLatencyMs, long refused) {}
    private record CacheTotals(long hits, long misses) {}
}
