package com.oep.corelms.config;

import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentGrade;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.course.*;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.LessonProgress;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.quiz.*;
import com.oep.coremodel.entity.registration.CourseInviteCode;
import com.oep.coremodel.entity.social.CourseAnnouncement;
import com.oep.coremodel.enums.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Sample data for demos (profile "demo", e.g. SPRING_PROFILES_ACTIVE=prod,demo): 2 teachers, 8 students, 3 published
 * courses with lessons, quiz, assignment, enrolments, progress, quiz results and graded work. Idempotent: skipped when the
 * demo teacher already exists. All demo accounts share one password ({@code oep.demo.password}).
 */
@Slf4j
@Component
@Profile("demo")
@Order(10) // after AdminSeeder
public class DemoDataSeeder implements ApplicationRunner {

    @PersistenceContext private EntityManager em;
    private final PasswordEncoder encoder;

    @Value("${oep.demo.password:Demo@12345}") private String demoPassword;

    public DemoDataSeeder(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!em.createQuery("select u from User u where u.email = 'gv.lan@oep.demo'").getResultList().isEmpty()) {
            log.info("Demo data already present, skipping");
            return;
        }
        User admin = em.createQuery("select u from User u where u.role = com.oep.coremodel.enums.UserRole.ADMIN", User.class)
                .setMaxResults(1).getSingleResult();

        User lan = user("gv.lan@oep.demo", "Nguyễn Thị Lan", UserRole.INSTRUCTOR, "Giảng viên Ngoại ngữ, 8 năm kinh nghiệm");
        User minh = user("gv.minh@oep.demo", "Trần Quang Minh", UserRole.INSTRUCTOR, "Kỹ sư phần mềm, giảng viên lập trình");
        String[] names = {"Lê Hoàng An", "Phạm Thu Hà", "Võ Minh Khôi", "Đặng Ngọc Mai", "Bùi Tuấn Kiệt", "Hoàng Gia Linh", "Ngô Đức Long", "Đỗ Phương Thảo"};
        List<User> students = new ArrayList<>();
        for (int i = 0; i < names.length; i++) students.add(user(String.format("hs%02d@oep.demo", i + 1), names[i], UserRole.STUDENT, null));

        Subject lang = subject("Ngoại ngữ", "Các khóa học ngoại ngữ");
        Subject it = subject("Công nghệ thông tin", "Lập trình và công nghệ");
        Subject soft = subject("Kỹ năng mềm", "Giao tiếp, thuyết trình, làm việc nhóm");

        Instant now = Instant.now();
        Course english = course(lang, "Tiếng Anh giao tiếp cơ bản", "Nghe, nói, phản xạ trong các tình huống hằng ngày: chào hỏi, mua sắm, du lịch.",
                LearningMode.FREE_NAVIGATION, 30, null, now.minus(Duration.ofDays(20)), now.plus(Duration.ofDays(70)), admin);
        Course python = course(it, "Lập trình Python nhập môn", "Từ biến và vòng lặp đến hàm và xử lý tệp; học tuần tự, có bài tập mỗi chương.",
                LearningMode.SEQUENTIAL, 40, new BigDecimal("500000"), now.minus(Duration.ofDays(10)), now.plus(Duration.ofDays(80)), admin);
        Course present = course(soft, "Kỹ năng thuyết trình", "Cấu trúc bài nói, ngôn ngữ cơ thể và cách trả lời câu hỏi.",
                LearningMode.FREE_NAVIGATION, 25, null, now.minus(Duration.ofDays(5)), now.plus(Duration.ofDays(60)), admin);
        assign(english, lan, admin); assign(present, lan, admin); assign(python, minh, admin);

        List<Lesson> enLessons = content(english, lan, new String[][]{
                {"Làm quen và chào hỏi", "Greetings", "Cách chào hỏi và giới thiệu bản thân. Hello, I'm ... Nice to meet you."},
                {"Hỏi đường và chỉ đường", "Directions", "Turn left, turn right, go straight, next to, opposite."},
                {"Mua sắm và trả giá", "Shopping", "How much is this? Can I try it on? It's too expensive."},
                {"Đặt phòng khách sạn", "Hotel", "I'd like to book a room for two nights, please."}});
        List<Lesson> pyLessons = content(python, minh, new String[][]{
                {"Biến và kiểu dữ liệu", "Variables", "x = 5; name = 'An'. Python có kiểu int, float, str, bool."},
                {"Câu lệnh điều kiện", "If/else", "if x > 3: ... elif ...: ... else: ..."},
                {"Vòng lặp for và while", "Loops", "for i in range(5): print(i). while điều_kiện: ..."},
                {"Hàm và module", "Functions", "def tong(a, b): return a + b. Dùng import để nạp module."}});
        List<Lesson> prLessons = content(present, lan, new String[][]{
                {"Cấu trúc một bài thuyết trình", "Structure", "Mở bài - thân bài - kết luận. Quy tắc 3 ý chính."},
                {"Ngôn ngữ cơ thể và giọng nói", "Delivery", "Giao tiếp bằng mắt, tư thế, tốc độ và ngắt nghỉ."},
                {"Xử lý câu hỏi khó", "Q&A", "Nhắc lại câu hỏi, trả lời ngắn gọn, thừa nhận khi chưa biết."},
                {"Thiết kế slide hiệu quả", "Slides", "Một ý một slide, hình ảnh thay cho chữ."}});

        Quiz enQuiz = quiz(english, enLessons.get(0), lan, "Kiểm tra: Chào hỏi và giao tiếp cơ bản",
                new Object[][]{{"Câu nào dùng để chào buổi sáng?", QuestionType.SINGLE_CHOICE, new String[]{"Good morning", "Good night", "Goodbye", "See you"}, 0},
                        {"\"Nice to meet you\" là câu dùng khi gặp ai đó lần đầu.", QuestionType.TRUE_FALSE, new String[]{"Đúng", "Sai"}, 0},
                        {"Chọn các câu dùng để hỏi đường.", QuestionType.MULTIPLE_CHOICE, new String[]{"Where is the station?", "How do I get to the bank?", "What time is it?", "Is there a pharmacy near here?"}, 0b1011},
                        {"Điền từ: How ___ is this shirt? (hỏi giá)", QuestionType.KEYWORD_FILL, new String[]{"much"}, 0}});
        Quiz pyQuiz = quiz(python, pyLessons.get(0), minh, "Kiểm tra: Biến và kiểu dữ liệu",
                new Object[][]{{"Kiểu dữ liệu của 3.14 trong Python là gì?", QuestionType.SINGLE_CHOICE, new String[]{"float", "int", "str", "bool"}, 0},
                        {"Python phân biệt chữ hoa và chữ thường trong tên biến.", QuestionType.TRUE_FALSE, new String[]{"Đúng", "Sai"}, 0},
                        {"Chọn các tên biến hợp lệ.", QuestionType.MULTIPLE_CHOICE, new String[]{"so_luong", "2ten", "_tong", "class"}, 0b0101},
                        {"Hàm nào in ra màn hình?", QuestionType.KEYWORD_FILL, new String[]{"print"}, 0}});
        Quiz prQuiz = quiz(present, prLessons.get(0), lan, "Kiểm tra: Cấu trúc bài thuyết trình",
                new Object[][]{{"Một bài thuyết trình thường gồm mấy phần chính?", QuestionType.SINGLE_CHOICE, new String[]{"3", "5", "7", "1"}, 0},
                        {"Nên nhồi thật nhiều chữ vào mỗi slide.", QuestionType.TRUE_FALSE, new String[]{"Đúng", "Sai"}, 1}});

        Assignment enHw = assignment(english, enLessons.get(1), lan, "Bài tập: Hội thoại hỏi đường", "Viết một đoạn hội thoại 6 câu giữa du khách và người bản địa về việc hỏi đường đến bảo tàng.");
        Assignment pyHw = assignment(python, pyLessons.get(2), minh, "Bài tập: Tính tổng và đếm số chẵn", "Viết chương trình nhập n và in ra tổng các số từ 1 đến n, đồng thời đếm có bao nhiêu số chẵn.");
        Assignment prHw = assignment(present, prLessons.get(0), lan, "Bài tập: Dàn ý bài thuyết trình 3 phút", "Lập dàn ý (mở - thân - kết) cho một bài thuyết trình 3 phút về chủ đề em chọn.");

        // enrolments: [student index] -> courses
        Course[] cs = {english, python, present};
        List<Lesson>[] ls = new List[]{enLessons, pyLessons, prLessons};
        Quiz[] qs = {enQuiz, pyQuiz, prQuiz};
        Assignment[] hs = {enHw, pyHw, prHw};
        User[] teachers = {lan, minh, lan};
        int[][] plan = {{0, 1}, {0, 1, 2}, {0, 2}, {1}, {0, 1}, {0, 2}, {1, 2}, {0}};
        double[] quizPct = {0.9, 0.75, 0.5, 0.85, 0.6, 1.0, 0.4, 0.8};
        CourseInviteCode[] codes = new CourseInviteCode[cs.length];
        String[] codeText = {"DEMO-ENGLISH", "DEMO-PYTHON", "DEMO-PRESENT"};
        for (int c = 0; c < cs.length; c++) { // enrolment is always via an invite code in this platform
            codes[c] = new CourseInviteCode();
            codes[c].setCourse(cs[c]); codes[c].setCode(codeText[c]); codes[c].setExpiresAt(now.plus(Duration.ofDays(90))); codes[c].setCreatedBy(admin);
            em.persist(codes[c]);
        }
        for (int s = 0; s < students.size(); s++) {
            for (int c : plan[s]) {
                Enrollment e = new Enrollment();
                e.setInviteCode(codes[c]);
                e.setCourse(cs[c]); e.setStudent(students.get(s)); e.setEnrolledAt(now.minus(Duration.ofDays(8 + s)));
                em.persist(e);
                int done = (s + c) % 5; // 0..4 lessons completed, giving a spread of progress
                for (int l = 0; l < ls[c].size(); l++) {
                    LessonProgress p = new LessonProgress();
                    p.setEnrollment(e); p.setLesson(ls[c].get(l));
                    if (l < done) {
                        p.setStatus(LessonProgressStatus.COMPLETED); p.setWatchedPercent(100); p.setTimeSpentSeconds(600 + 120 * l);
                        p.setStartedAt(now.minus(Duration.ofDays(6))); p.setCompletedAt(now.minus(Duration.ofDays(5)));
                    } else if (l == done && done > 0) {
                        p.setStatus(LessonProgressStatus.IN_PROGRESS); p.setWatchedPercent(40); p.setStartedAt(now.minus(Duration.ofDays(1)));
                    } else continue;
                    em.persist(p);
                }
                if (s % 4 != 3) { // most students already took the quiz
                    QuizAttempt a = new QuizAttempt();
                    a.setQuiz(qs[c]); a.setEnrollment(e); a.setAttemptNumber(1); a.setStatus(QuizAttemptStatus.GRADED);
                    a.setStartedAt(now.minus(Duration.ofDays(4))); a.setSubmittedAt(now.minus(Duration.ofDays(4)).plusSeconds(540));
                    em.persist(a);
                    QuizResult r = new QuizResult();
                    BigDecimal score = BigDecimal.valueOf(Math.round(quizPct[(s + c) % 8] * 100)).setScale(2);
                    r.setAttempt(a); r.setAutoScore(score); r.setFinalScore(score);
                    em.persist(r);
                }
                if (s % 3 != 2) { // assignments: most submitted, two thirds graded
                    AssignmentSubmission sub = new AssignmentSubmission();
                    sub.setAssignment(hs[c]); sub.setEnrollment(e); sub.setSubmittedAt(now.minus(Duration.ofDays(3)));
                    sub.setTextContent("Bài làm của " + students.get(s).getEmail() + ": đây là phần trả lời cho \"" + hs[c].getTitle() + "\".");
                    if (s % 2 == 0) {
                        sub.setStatus(AssignmentStatus.GRADED);
                        em.persist(sub);
                        AssignmentGrade g = new AssignmentGrade();
                        g.setSubmission(sub); g.setScore(BigDecimal.valueOf(60 + ((s * 7 + c * 5) % 36)).setScale(2));
                        g.setFeedback("Bài làm khá tốt, cần chú ý trình bày rõ ràng hơn ở phần kết luận.");
                        g.setGradedBy(teachers[c]); g.setGradedAt(now.minus(Duration.ofDays(2)));
                        em.persist(g);
                    } else {
                        em.persist(sub);
                    }
                }
            }
        }
        for (int c = 0; c < cs.length; c++) {
            CourseAnnouncement an = new CourseAnnouncement();
            an.setCourse(cs[c]); an.setAuthor(teachers[c]);
            an.setTitle("Chào mừng đến với khóa " + cs[c].getTitle());
            an.setContent("Chào các bạn! Hãy xem lộ trình bài học, làm quiz đầu tiên và nộp bài tập trước hạn nhé.");
            em.persist(an);
        }
        log.warn("DEMO DATA created. Accounts (password for all: {}): gv.lan@oep.demo, gv.minh@oep.demo, hs01..hs08@oep.demo. Invite codes: DEMO-ENGLISH, DEMO-PYTHON, DEMO-PRESENT", demoPassword);
    }

    private User user(String email, String fullName, UserRole role, String bio) {
        User u = new User();
        u.setEmail(email); u.setRole(role); u.setAccountStatus(AccountStatus.ACTIVE);
        em.persist(u);
        AuthIdentity id = new AuthIdentity();
        id.setUser(u); id.setProvider(AuthProvider.LOCAL); id.setPasswordHash(encoder.encode(demoPassword)); id.setMustChangePassword(false);
        em.persist(id);
        UserProfile p = new UserProfile();
        p.setUser(u); p.setFullName(fullName); p.setBio(bio);
        em.persist(p);
        return u;
    }

    private Subject subject(String name, String description) {
        Subject s = new Subject();
        s.setName(name); s.setDescription(description);
        em.persist(s);
        return s;
    }

    private Course course(Subject subject, String title, String description, LearningMode mode, int capacity, BigDecimal fee, Instant start, Instant end, User admin) {
        Course c = new Course();
        c.setSubject(subject); c.setTitle(title); c.setDescription(description); c.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        c.setLearningMode(mode); c.setCapacity(capacity); c.setFeeAmount(fee); c.setStartAt(start); c.setEndAt(end);
        c.setPassingPercent(50); c.setQuizWeightPercent(50); c.setCreatedBy(admin);
        em.persist(c);
        return c;
    }

    private void assign(Course course, User teacher, User admin) {
        CourseInstructor ci = new CourseInstructor();
        ci.setId(new CourseInstructorId(course.getId(), teacher.getId()));
        ci.setCourse(course); ci.setInstructor(teacher); ci.setAddedBy(admin);
        em.persist(ci);
    }

    /** rows: {lesson title, content title, body}. Two modules, two lessons each; every lesson has a text and a video link. */
    private List<Lesson> content(Course course, User author, String[][] rows) {
        List<Lesson> out = new ArrayList<>();
        CourseModule m = null;
        for (int i = 0; i < rows.length; i++) {
            if (i % 2 == 0) {
                m = new CourseModule();
                m.setCourse(course); m.setTitle("Chương " + (i / 2 + 1)); m.setPosition(i / 2);
                em.persist(m);
            }
            Lesson l = new Lesson();
            l.setModule(m); l.setTitle(rows[i][0]); l.setPosition(i % 2); l.setDescription(rows[i][1]);
            em.persist(l);
            LessonContent text = new LessonContent();
            text.setLesson(l); text.setContentType(ContentType.TEXT); text.setTitle("Nội dung bài học"); text.setTextContent(rows[i][2]);
            text.setUploadStatus(UploadStatus.AVAILABLE); text.setPosition(0); text.setCreatedBy(author);
            em.persist(text);
            LessonContent link = new LessonContent();
            link.setLesson(l); link.setContentType(ContentType.LINK); link.setTitle("Tham khảo thêm"); link.setExternalUrl("https://www.youtube.com/results?search_query=" + rows[i][1].replace(' ', '+'));
            link.setUploadStatus(UploadStatus.AVAILABLE); link.setPosition(1); link.setCreatedBy(author);
            em.persist(link);
            out.add(l);
        }
        return out;
    }

    /** rows: {text, type, options[], correct option index; for MULTIPLE_CHOICE a bitmask of correct options}. */
    private Quiz quiz(Course course, Lesson lesson, User author, String title, Object[][] rows) {
        Quiz q = new Quiz();
        q.setCourse(course); q.setLesson(lesson); q.setTitle(title); q.setDescription("Làm trong 15 phút, được làm 2 lần, lấy điểm cao nhất.");
        q.setTimeLimitSeconds(900); q.setMaxAttempts(2); q.setGradingPolicy(QuizGradingPolicy.HIGHEST);
        q.setShowScore(true); q.setShowCorrectAnswers(true); q.setDeadline(Instant.now().plus(Duration.ofDays(30))); q.setCreatedBy(author);
        em.persist(q);
        QuestionBank bank = new QuestionBank();
        bank.setCourse(course); bank.setName("Ngân hàng " + course.getTitle()); bank.setCreatedBy(author);
        em.persist(bank);
        for (int i = 0; i < rows.length; i++) {
            QuestionType type = (QuestionType) rows[i][1];
            String[] options = (String[]) rows[i][2];
            int correct = (Integer) rows[i][3];
            Question qu = new Question();
            qu.setQuestionBank(bank); qu.setQuestionType(type); qu.setQuestionText((String) rows[i][0]); qu.setCreatedBy(author);
            if (type == QuestionType.KEYWORD_FILL) qu.setKeywordAnswer(options[0]);
            em.persist(qu);
            if (type != QuestionType.KEYWORD_FILL) {
                for (int o = 0; o < options.length; o++) {
                    QuestionOption opt = new QuestionOption();
                    opt.setQuestion(qu); opt.setOptionText(options[o]); opt.setPosition(o);
                    opt.setCorrect(type == QuestionType.MULTIPLE_CHOICE ? (correct & (1 << o)) != 0 : o == correct);
                    em.persist(opt);
                }
            }
            QuizQuestion link = new QuizQuestion();
            link.setId(new QuizQuestionId(q.getId(), qu.getId()));
            link.setQuiz(q); link.setQuestion(qu); link.setPosition(i); link.setPoints(BigDecimal.valueOf(25).setScale(2));
            em.persist(link);
        }
        return q;
    }

    private Assignment assignment(Course course, Lesson lesson, User author, String title, String instructions) {
        Assignment a = new Assignment();
        a.setCourse(course); a.setLesson(lesson); a.setTitle(title); a.setInstructions(instructions);
        a.setDeadline(Instant.now().plus(Duration.ofDays(14))); a.setMaxSubmissions(2); a.setAllowText(true); a.setAllowFiles(true);
        a.setMaxScore(new BigDecimal("100.00")); a.setCreatedBy(author);
        em.persist(a);
        return a;
    }
}
