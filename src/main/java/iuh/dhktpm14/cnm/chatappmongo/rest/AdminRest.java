package iuh.dhktpm14.cnm.chatappmongo.rest;

import io.swagger.annotations.ApiOperation;
import iuh.dhktpm14.cnm.chatappmongo.dto.UserProfileDto;
import iuh.dhktpm14.cnm.chatappmongo.entity.User;
import iuh.dhktpm14.cnm.chatappmongo.mapper.UserMapper;
import iuh.dhktpm14.cnm.chatappmongo.service.AppUserDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@CrossOrigin("${spring.security.cross_origin}")
public class AdminRest {

    @Autowired
    private AppUserDetailService userDetailService;

    @Autowired
    private UserMapper userMapper;

    @GetMapping(value = "/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @ApiOperation("Lấy danh sách tất cả user")
    public ResponseEntity<?> getAllUser(@ApiIgnore @AuthenticationPrincipal User user, Pageable pageable) {
        log.info("admin with username = {} getting all user", user.getUsername());
        Page<User> findAll = userDetailService.findAll(pageable);
        return ResponseEntity.ok(toUserProfileDto(findAll));
    }

    @PostMapping("/users/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @ApiOperation("Tìm kiếm user theo tên hoặc số điện thoại gần đúng")
    public ResponseEntity<?> searchUser(@ApiIgnore @AuthenticationPrincipal User user, @RequestParam String textToSearch) {
        log.info("admin with username = {} finding user with key = {}", user.getUsername(), textToSearch);
        List<User> result = userDetailService.findAllByDisplayNameContainingIgnoreCaseOrPhoneNumberContainingIgnoreCaseOrderByDisplayNameAsc(textToSearch, textToSearch);
        if (result == null) {
            return ResponseEntity.ok(new ArrayList<>(0));
        }
        List<UserProfileDto> userProfiles = result.stream().filter(x -> ! x.getId().equals(user.getId()))
                .map(userMapper::toUserProfileDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userProfiles);
    }

    /**
     *
     */
    private Page<?> toUserProfileDto(Page<User> userPage) {
        List<User> content = userPage.getContent();
        List<UserProfileDto> dto = content.stream()
                .map(x -> userMapper.toUserProfileDto(x))
                .collect(Collectors.toList());
        return new PageImpl<>(dto, userPage.getPageable(), userPage.getTotalElements());
    }

}