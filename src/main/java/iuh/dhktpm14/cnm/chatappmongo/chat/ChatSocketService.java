package iuh.dhktpm14.cnm.chatappmongo.chat;

import iuh.dhktpm14.cnm.chatappmongo.entity.Inbox;
import iuh.dhktpm14.cnm.chatappmongo.entity.InboxMessage;
import iuh.dhktpm14.cnm.chatappmongo.entity.Member;
import iuh.dhktpm14.cnm.chatappmongo.entity.Message;
import iuh.dhktpm14.cnm.chatappmongo.entity.Room;
import iuh.dhktpm14.cnm.chatappmongo.mapper.MessageMapper;
import iuh.dhktpm14.cnm.chatappmongo.repository.InboxMessageRepository;
import iuh.dhktpm14.cnm.chatappmongo.repository.InboxRepository;
import iuh.dhktpm14.cnm.chatappmongo.repository.MessageRepository;
import iuh.dhktpm14.cnm.chatappmongo.service.InboxService;
import iuh.dhktpm14.cnm.chatappmongo.service.ReadTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ChatSocketService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ReadTrackingService readTrackingService;

    @Autowired
    private InboxService inboxService;

    private static final Logger logger = Logger.getLogger(ChatSocketService.class.getName());

    public void sendSystemMessage(Message message, Room room, String senderId) {
        message.setSenderId(null);
        sendMessage(message, room, senderId);
    }

    public void sendMessage(Message message, Room room, String senderId) {
        messageRepository.save(message);
        saveMessageToDatabase(message, room);
        inboxService.updateLastTimeForAllInboxOfRoom(room);
        readTrackingService.incrementUnReadMessageForMembersOfRoomExcludeUserId(room, senderId, message.getId());
        readTrackingService.updateReadTracking(senderId, room.getId(), message.getId());
        sendMessageToAllMemberOfRoom(message, room);
    }

    /**
     * gửi message tới tất cả các thành viên qua websocket, chưa lưu xuông db
     */
    private void sendMessageToAllMemberOfRoom(Message message, Room room) {
        Set<Member> members = room.getMembers();
        if (members != null && ! members.isEmpty()) {
            for (Member m : members) {
                logger.log(Level.INFO, "sending message id={0} to userId={1}", new Object[]{ message.getId(), m.getUserId() });
                messagingTemplate.convertAndSendToUser(m.getUserId(), "/queue/messages",
                        messageMapper.toMessageToClient(message));
            }
        }
    }

    /**
     * lưu tin nhắn và liên kết danh sách inbox của tất cả thành viên với message này
     */
    private void saveMessageToDatabase(Message message, Room room) {
        List<Inbox> inboxes = getAllInboxOfRoomToSaveMessage(room);
        if (! inboxes.isEmpty()) {
            for (Inbox inbox : inboxes) {
                var inboxMessage = InboxMessage.builder()
                        .inboxId(inbox.getId())
                        .messageId(message.getId())
                        .messageCreateAt(message.getCreateAt())
                        .build();
                inboxMessageRepository.save(inboxMessage);
            }
        }
    }

    /**
     * lấy danh sách inbox của tất cả thành viên trong room,
     * nếu thành viên nào trong room chưa có inbox thì tạo inbox cho thành viên đó
     */
    private List<Inbox> getAllInboxOfRoomToSaveMessage(Room room) {
        List<Inbox> inboxes = new ArrayList<>();
        Set<Member> members = room.getMembers();
        if (members != null && ! members.isEmpty())
            for (Member m : members) {
                // tìm danh sách inbox của tất cả các thành viên
                // nếu có thì thêm vào danh sách
                var inboxOptional = inboxRepository.findByOfUserIdAndRoomId(m.getUserId(), room.getId());
                Inbox inbox;
                if (inboxOptional.isPresent()) {
                    inbox = inboxOptional.get();
                    if (inbox.isEmpty())
                        inboxService.updateEmptyStatusInbox(inbox.getId(), false);
                } else {
                    // nếu member chưa có inbox thì tạo mới rồi thêm vào danh sách
                    inbox = Inbox.builder().ofUserId(m.getUserId())
                            .roomId(room.getId()).empty(false).build();
                    inboxRepository.save(inbox);
                }
                inboxes.add(inbox);
            }
        return inboxes;
    }

}
