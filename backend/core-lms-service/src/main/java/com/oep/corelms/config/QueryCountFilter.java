package com.oep.corelms.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Dev-only N+1 detector: logs every request that issues many SQL statements (enable with profile "dev"). */
@Slf4j
@Component
@Profile("dev")
public class QueryCountFilter extends OncePerRequestFilter {

    private final Statistics stats;

    public QueryCountFilter(EntityManagerFactory emf) {
        this.stats = emf.unwrap(SessionFactory.class).getStatistics();
        this.stats.setStatisticsEnabled(true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long before = stats.getPrepareStatementCount();
        try {
            chain.doFilter(req, res);
        } finally {
            long n = stats.getPrepareStatementCount() - before;
            if (n > 15) log.warn("N+1 suspect: {} {} issued {} SQL statements", req.getMethod(), req.getRequestURI(), n);
            else log.debug("{} {} -> {} SQL", req.getMethod(), req.getRequestURI(), n);
        }
    }
}
