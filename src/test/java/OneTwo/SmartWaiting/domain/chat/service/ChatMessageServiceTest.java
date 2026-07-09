package OneTwo.SmartWaiting.domain.chat.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageSendRequestDto;
import OneTwo.SmartWaiting.domain.chat.entity.ChatMessage;
import OneTwo.SmartWaiting.domain.chat.entity.ChatRoom;
import OneTwo.SmartWaiting.domain.chat.enums.ChatMessageType;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.chat.redis.RedisChatPublisher;
import OneTwo.SmartWaiting.domain.chat.repository.ChatMessageRepository;
import OneTwo.SmartWaiting.domain.chat.repository.ChatRoomRepository;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final String USER_EMAIL = "user@test.com";
    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RedisChatPublisher redisChatPublisher;

    private Member stubMember() {
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(USER_ID);
        lenient().when(member.getNickname()).thenReturn("고객");
        when(memberRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(member));
        return member;
    }

    private ChatRoom stubRoom(ChatRoomStatus status, boolean participant) {
        ChatRoom room = mock(ChatRoom.class);
        lenient().when(room.getId()).thenReturn(ROOM_ID);
        lenient().when(room.getStatus()).thenReturn(status);
        lenient().when(room.getIsDeleted()).thenReturn(false);
        lenient().when(room.isParticipant(USER_ID)).thenReturn(participant);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        return room;
    }

    // ================= [ 메시지 전송 ] =================

    @Test
    @DisplayName("메시지 전송 성공 - DB에 저장한 뒤 저장된 내용으로 Redis에 발행한다.")
    void sendMessage_Participant_SavesAndPublishes() {
        // given
        Member sender = stubMember();
        ChatRoom room = stubRoom(ChatRoomStatus.ACTIVE, true);

        ChatMessage saved = mock(ChatMessage.class);
        when(saved.getId()).thenReturn(100L);
        when(saved.getChatRoom()).thenReturn(room);
        when(saved.getSender()).thenReturn(sender);
        when(saved.getContent()).thenReturn("환불 문의드립니다.");
        when(saved.isRead()).thenReturn(false);
        when(saved.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        // when
        chatMessageService.sendMessage(USER_EMAIL, ROOM_ID, new ChatMessageSendRequestDto("환불 문의드립니다."));

        // then
        verify(chatMessageRepository).save(any(ChatMessage.class));
        ArgumentCaptor<ChatMessageResponseDto> captor = ArgumentCaptor.forClass(ChatMessageResponseDto.class);
        verify(redisChatPublisher).publish(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.TALK);
        assertThat(captor.getValue().content()).isEqualTo("환불 문의드립니다.");
    }

    @Test
    @DisplayName("메시지 전송 실패 - 종료된 방이면 CHATROOM_ALREADY_CLOSED 예외가 발생한다.")
    void sendMessage_ClosedRoom_ThrowsChatroomAlreadyClosed() {
        // given
        stubMember();
        stubRoom(ChatRoomStatus.CLOSED, true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage(USER_EMAIL, ROOM_ID, new ChatMessageSendRequestDto("안녕하세요")));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHATROOM_ALREADY_CLOSED);
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("메시지 전송 실패 - 방 참여자가 아니면 NOT_CHATROOM_PARTICIPANT 예외가 발생한다.")
    void sendMessage_NotParticipant_ThrowsNotChatroomParticipant() {
        // given
        stubMember();
        stubRoom(ChatRoomStatus.ACTIVE, false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage(USER_EMAIL, ROOM_ID, new ChatMessageSendRequestDto("안녕하세요")));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("메시지 전송 실패 - 존재하지 않는 방이면 CHATROOM_NOT_FOUND 예외가 발생한다.")
    void sendMessage_RoomNotFound_ThrowsChatroomNotFound() {
        // given
        stubMember();
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.sendMessage(USER_EMAIL, ROOM_ID, new ChatMessageSendRequestDto("안녕하세요")));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHATROOM_NOT_FOUND);
    }

    // ================= [ 읽음 처리 ] =================

    @Test
    @DisplayName("읽음 처리 성공 - 상대방 메시지를 벌크 읽음 처리하고 READ 영수증을 발행한다.")
    void markAsRead_HasUnread_BulkUpdatesAndPublishesReadReceipt() {
        // given
        stubMember();
        stubRoom(ChatRoomStatus.ACTIVE, true);
        when(chatMessageRepository.markAllAsRead(ROOM_ID, USER_ID)).thenReturn(3);

        // when
        chatMessageService.markAsRead(USER_EMAIL, ROOM_ID);

        // then
        ArgumentCaptor<ChatMessageResponseDto> captor = ArgumentCaptor.forClass(ChatMessageResponseDto.class);
        verify(redisChatPublisher).publish(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.READ);
        assertThat(captor.getValue().senderId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("읽음 처리 - 읽을 메시지가 없으면 READ 이벤트를 발행하지 않는다.")
    void markAsRead_NothingToRead_SkipsPublish() {
        // given
        stubMember();
        stubRoom(ChatRoomStatus.ACTIVE, true);
        when(chatMessageRepository.markAllAsRead(ROOM_ID, USER_ID)).thenReturn(0);

        // when
        chatMessageService.markAsRead(USER_EMAIL, ROOM_ID);

        // then
        verify(redisChatPublisher, never()).publish(anyLong(), any(ChatMessageResponseDto.class));
    }

    @Test
    @DisplayName("읽음 처리 실패 - 방 참여자가 아니면 NOT_CHATROOM_PARTICIPANT 예외가 발생한다.")
    void markAsRead_NotParticipant_ThrowsNotChatroomParticipant() {
        // given
        stubMember();
        stubRoom(ChatRoomStatus.ACTIVE, false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.markAsRead(USER_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        verify(chatMessageRepository, never()).markAllAsRead(anyLong(), anyLong());
    }
}
