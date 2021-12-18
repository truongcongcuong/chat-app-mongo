package iuh.dhktpm14.cnm.chatappmongo.service;

import iuh.dhktpm14.cnm.chatappmongo.dto.StatisticsSignUpByGender;
import iuh.dhktpm14.cnm.chatappmongo.dto.UserSignupDto;
import iuh.dhktpm14.cnm.chatappmongo.entity.User;
import iuh.dhktpm14.cnm.chatappmongo.enumvalue.OnlineStatus;
import iuh.dhktpm14.cnm.chatappmongo.enumvalue.RoleType;
import iuh.dhktpm14.cnm.chatappmongo.projection.CustomAggregationOperation;
import iuh.dhktpm14.cnm.chatappmongo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Service
public class AppUserDetailService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MessageSource messageSource;

    private static final Random random = new Random();

    private static final Logger logger = Logger.getLogger(AppUserDetailService.class.getName());

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.log(Level.INFO, "load user by user name = {0}", username);

        return userRepository.findDistinctByPhoneNumberOrUsernameOrEmail(username)
                .orElseThrow(() -> {
                    String message = messageSource.getMessage("username_not_found",
                            new Object[]{ username }, LocaleContextHolder.getLocale());
                    return new UsernameNotFoundException(message);
                });
    }

    public User signupEmail(UserSignupDto userDto) {
        var user = User.builder()
                .id(userDto.getId())
                .displayName(userDto.getDisplayName())
                .password(encoder.encode(userDto.getPassword()))
                .email(userDto.getEmail())
                .phoneNumber(userDto.getPhoneNumber())
                .enable(false)
                .createAt(new Date())
                .roles(RoleType.ROLE_USER.toString())
                .build();
        return userRepository.save(user);
    }

    public User signupPhone(UserSignupDto userDto) {
        var user = User.builder()
                .id(userDto.getId())
                .displayName(userDto.getDisplayName())
                .password(encoder.encode(userDto.getPassword()))
                .email(userDto.getEmail())
                .phoneNumber(userDto.getPhoneNumber())
                .enable(true)
                .createAt(new Date())
                .roles(RoleType.ROLE_USER.toString())
                .build();
        return userRepository.save(user);
    }

    public void sendVerificationEmail(User user) throws UnsupportedEncodingException, MessagingException {
        int verificationCode = random.nextInt((999999 - 100000) + 1) + 100000;
        user.setVerificationCode(String.valueOf(verificationCode));
        userRepository.save(user);

        logger.log(Level.INFO, "email verification code = {0}", String.valueOf(verificationCode));

        String toAddress = user.getEmail();
        var fromAddress = "chat_app_email";
        var senderName = messageSource.getMessage("verification_sender_name_in_mail", null, LocaleContextHolder.getLocale());
        var subject = messageSource.getMessage("verification_subject_in_mail", null, LocaleContextHolder.getLocale());
        var content = messageSource.getMessage("verification_content_in_mail",
                new Object[]{ user.getDisplayName(), String.valueOf(verificationCode) }, LocaleContextHolder.getLocale());

        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message);

        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setSubject(subject);
        helper.setText(content, true);

        logger.log(Level.INFO, "sending verification email to email address = {0}", toAddress);

        /*
        đưa vào thread khác để client không phải đợi
         */
        new Thread(() -> mailSender.send(message)).start();

    }

    public boolean regexEmail(String email) {
        var pattern = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
        var matcher = pattern.matcher(email);
        return matcher.find();
    }

    /**
     * cập nhật refreshToken cho userId
     */
    public void setRefreshToken(String userId, String refreshToken) {
        logger.log(Level.INFO, "refresh token = {0}", refreshToken);
        var criteria = Criteria.where("_id").is(userId);
        var update = new Update();
        update.set("refreshToken", refreshToken);
        mongoTemplate.updateFirst(Query.query(criteria), update, User.class);
    }

    /*
    cập nhật trạng thái là đang online
     */
    public void updateStatusOnline(String userId) {
        logger.log(Level.INFO, "updating online status for userId = {0}", userId);
        var criteria = Criteria.where("_id").is(userId);
        var update = new Update();
        update.set("onlineStatus", OnlineStatus.ONLINE)
                .unset("lastOnline");
        mongoTemplate.updateFirst(Query.query(criteria), update, User.class);
    }

    /*
    cập nhật trạng thái là đã offline
     */
    public void updateStatusOffline(String userId) {
        logger.log(Level.INFO, "updating offline status and time for userId = {0}", userId);
        var criteria = Criteria.where("_id").is(userId);
        var update = new Update();
        update.set("onlineStatus", OnlineStatus.OFFLINE)
                .set("lastOnline", new Date());
        mongoTemplate.updateFirst(Query.query(criteria), update, User.class);
    }

    //    @Cacheable("user")
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findDistinctByEmail(email);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * tìm kiếm user theo tên gần đúng, k phân biệt hoa thường
     */
    public List<User> findAllByDisplayNameContainingIgnoreCaseOrPhoneNumberContainingIgnoreCaseOrderByDisplayNameAsc(String displayName, String phoneNumber) {
        return userRepository.findAllByDisplayNameContainingIgnoreCaseOrPhoneNumberContainingIgnoreCaseOrderByDisplayNameAsc(displayName, phoneNumber);
    }

    public Page<User> findAllByDisplayNameContainingIgnoreCaseOrPhoneNumberContainingIgnoreCaseOrderByDisplayNameAsc(String displayName, String phoneNumber, Pageable pageable) {
        return userRepository.findAllByDisplayNameContainingIgnoreCaseOrPhoneNumberContainingIgnoreCaseOrderByDisplayNameAsc(displayName, phoneNumber, pageable);
    }

    public List<User> findByIdIn(List<String> ids) {
        return userRepository.findByIdIn(ids);
    }

    public List<User> findByCreateAtBetween(Date from, Date to) {
        return userRepository.findByCreateAtBetween(from, to);
    }

    public Optional<User> findDistinctByUsername(String userName) {
        return userRepository.findDistinctByUsername(userName);
    }

    public Optional<User> findDistinctByPhoneNumber(String phoneNumber) {
        return userRepository.findDistinctByPhoneNumber(phoneNumber);
    }

    public Optional<User> findDistinctByPhoneNumberOrUsernameOrEmail(String phoneNumber) {
        return userRepository.findDistinctByPhoneNumberOrUsernameOrEmail(phoneNumber);
    }

    public Page<User> findAllByRoles(String roles, Pageable pageable) {
        return userRepository.findAllByRoles(roles, pageable);
    }

    public boolean existsById(String userId) {
        return userRepository.existsById(userId);
    }

    public boolean existsByUsername(String userName) {
        return userRepository.existsByUsername(userName);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }


    //aggregate([
    // {$project:{_id:0,phoneNumber:1,male:{$cond:[{$eq:['$gender','Nam']},1,0]},female:{$cond:[{$eq:['$gender','Nu']},1,0]},year:{$year:'$createAt'},month:{$month:'$createAt'}}},
    // {$match:{year:{$eq:2021}}},
    // {$group:{_id:'$month',male:{$sum:'$male'},female:{$sum:'$female'}}}])
    public List<StatisticsSignUpByGender> statisticsSignUpByGender(int year) {
        var aggregation = Aggregation.newAggregation(
                new CustomAggregationOperation("{$project:{_id:0,phoneNumber:1,male:{$cond:[{$eq:['$gender','Nam']},1,0]},female:{$cond:[{$eq:['$gender','Nu']},1,0]},year:{$year:'$createAt'},month:{$month:'$createAt'}}}"),
                new CustomAggregationOperation("{$match:{year:{$eq:2021}}}"),
                new CustomAggregationOperation("{$group:{_id:'$month',male:{$sum:'$male'},female:{$sum:'$female'}}}"),
                new CustomAggregationOperation("{$project:{_id:0,month:'$_id',male:1,female:1}}"),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "month"))
        );

        AggregationResults<StatisticsSignUpByGender> statistics = mongoTemplate.aggregate(aggregation, "user", StatisticsSignUpByGender.class);
        return statistics.getMappedResults();
    }

}
