package iuh.dhktpm14.cnm.chatappmongo.rest;

import iuh.dhktpm14.cnm.chatappmongo.entity.Member;
import iuh.dhktpm14.cnm.chatappmongo.entity.Room;
import iuh.dhktpm14.cnm.chatappmongo.entity.User;
import iuh.dhktpm14.cnm.chatappmongo.enumvalue.RoomType;
import iuh.dhktpm14.cnm.chatappmongo.exceptions.MyException;
import iuh.dhktpm14.cnm.chatappmongo.exceptions.RoomNotFoundException;
import iuh.dhktpm14.cnm.chatappmongo.exceptions.UnAuthenticateException;
import iuh.dhktpm14.cnm.chatappmongo.mapper.RoomMapper;
import iuh.dhktpm14.cnm.chatappmongo.repository.MessageRepository;
import iuh.dhktpm14.cnm.chatappmongo.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/rooms")
@CrossOrigin(value = "${spring.security.cross_origin}")
public class RoomRest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoomMapper roomMapper;

    /**
     * endpoint lấy số tin nhắn mới theo roomId, nếu cần
     */
    @GetMapping("/{roomId}/count-new-message")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> countNewMessage(@AuthenticationPrincipal User user, @PathVariable String roomId) {
        if (user == null)
            throw new UnAuthenticateException();
        return ResponseEntity.ok(messageRepository.countNewMessage(roomId, user.getId()));
    }

    /**
     * endpoint lấy tin nhắn cuối theo roomId, nếu cần
     */
    @GetMapping("/{roomId}/last-message")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> lastMessage(@PathVariable String roomId) {
        return ResponseEntity.ok(messageRepository.findLastMessageByRoomId(roomId));
    }

    /**
     * tạo nhóm mới
     */
    @PostMapping("/group")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> lastMessage(@RequestBody Room room, @AuthenticationPrincipal User user) {
        if (user == null)
            throw new UnAuthenticateException();
        room.setCreateByUserId(user.getId());
        room.setType(RoomType.GROUP);
        if (room.getMembers() == null || room.getMembers().isEmpty())
            throw new MyException("Chưa có thành viên");
        for (Member m : room.getMembers()) {
            if (! m.getUserId().equals(user.getId())) {
                m.setAddByUserId(user.getId());
                m.setAddTime(new Date());
            }
        }
        room.getMembers().add(Member.builder().userId(user.getId()).build());
        return ResponseEntity.ok(roomRepository.save(room));
    }

    /**
     * thêm thành viên cho nhóm
     */
    @PostMapping("/group/{roomId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> lastMessage(@PathVariable String roomId, @RequestBody List<Member> members, @AuthenticationPrincipal User user) {
        if (user == null)
            throw new UnAuthenticateException();
        Optional<Room> roomOptional = roomRepository.findById(roomId);
        if (roomOptional.isEmpty())
            throw new RoomNotFoundException();
        var room = roomOptional.get();
        if (room.getType().equals(RoomType.ONE))
            throw new MyException("Không thể thêm thành viên. Vui lòng tạo nhóm mới");
        for (Member m : members) {
            if (! m.getUserId().equals(user.getId())) {
                m.setAddByUserId(user.getId());
                m.setAddTime(new Date());
            }
        }
        room.getMembers().addAll(members);
        return ResponseEntity.ok(roomRepository.save(room));
    }

    /**
     * chuyển từ Page<Room> qua Page<RoomDto>
     */
    private Page<?> toRoomDto(Page<Room> roomPage) {
        List<Room> content = roomPage.getContent();
        List<Object> dto = content.stream().map(x -> roomMapper.toRoomSummaryDto(x.getId())).collect(Collectors.toList());
        return new PageImpl<>(dto, roomPage.getPageable(), roomPage.getTotalElements());
    }

}