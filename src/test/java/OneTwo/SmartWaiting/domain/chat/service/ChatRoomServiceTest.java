package OneTwo.SmartWaiting.domain.chat.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import OneTwo.SmartWaiting.domain.chat.dto.ChatRoomResponseDto;
import OneTwo.SmartWaiting.domain.chat.entity.ChatRoom;
import OneTwo.SmartWaiting.domain.chat.enums.ChatMessageType;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.chat.redis.RedisChatPublisher;
import OneTwo.SmartWaiting.domain.chat.repository.ChatMessageRepository;
import OneTwo.SmartWaiting.domain.chat.repository.ChatRoomRepository;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
class ChatRoomServiceTest {

    private static final String USER_EMAIL = "user@test.com";
    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final Long USER_ID = 1L;
    private static final Long ADMIN_ID = 9L;
    private static final Long ROOM_ID = 10L;

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RedisChatPublisher redisChatPublisher;

    /** DTO 변환 등에서 일부 getter만 쓰이는 경우가 많아 lenient로 공통 스텁한다. */
    private Member stubMember(String email, Long id, String nickname, UserRole role) {
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(id);
        lenient().when(member.getNickname()).thenReturn(nickname);
        lenient().when(member.getRole()).thenReturn(role);
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));
        return member;
    }

    private ChatRoom stubRoom(Member member, Member admin, ChatRoomStatus status) {
        ChatRoom room = mock(ChatRoom.class);
        lenient().when(room.getId()).thenReturn(ROOM_ID);
        lenient().when(room.getMember()).thenReturn(member);
        lenient().when(room.getAdmin()).thenReturn(admin);
        lenient().when(room.getStatus()).thenReturn(status);
        lenient().when(room.getCreatedAt()).thenReturn(LocalDateTime.now());
        lenient().when(room.getIsDeleted()).thenReturn(false);
        return room;
    }

    // ================= [ 방 생성 ] =================

    @Test
    @DisplayName("방 생성 성공 - 열린 방이 없으면 WAITING 상태 새 방을 만든다.")
    void createRoom_NoOpenRoom_CreatesWaitingRoom() {
        // given
        Member member = stubMember(USER_EMAIL, USER_ID, "고객", UserRole.USER);
        when(chatRoomRepository.findFirstByMemberIdAndStatusNotAndIsDeletedFalse(USER_ID, ChatRoomStatus.CLOSED))
                .thenReturn(Optional.empty());
        ChatRoom saved = stubRoom(member, null, ChatRoomStatus.WAITING);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(saved);

        // when
        ChatRoomResponseDto result = chatRoomService.createRoom(USER_EMAIL);

        // then
        assertThat(result.status()).isEqualTo(ChatRoomStatus.WAITING);
        assertThat(result.unreadCount()).isZero();
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("방 생성 멱등 - 이미 열린 방이 있으면 새로 만들지 않고 그 방을 반환한다.")
    void createRoom_OpenRoomExists_ReturnsExistingRoom() {
        // given
        Member member = stubMember(USER_EMAIL, USER_ID, "고객", UserRole.USER);
        ChatRoom existing = stubRoom(member, null, ChatRoomStatus.WAITING);
        when(chatRoomRepository.findFirstByMemberIdAndStatusNotAndIsDeletedFalse(USER_ID, ChatRoomStatus.CLOSED))
                .thenReturn(Optional.of(existing));
        when(chatMessageRepository.countByChatRoomIdAndSenderIdNotAndIsReadFalse(ROOM_ID, USER_ID))
                .thenReturn(2L);

        // when
        ChatRoomResponseDto result = chatRoomService.createRoom(USER_EMAIL);

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.unreadCount()).isEqualTo(2L);
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    // ================= [ 상담 수락 (claim) ] =================

    @Test
    @DisplayName("상담 수락 성공 - 담당자를 배정하고 SYSTEM 메시지를 발행한다.")
    void claimRoom_WaitingRoom_AssignsAdminAndPublishesSystemMessage() {
        // given
        Member admin = stubMember(ADMIN_EMAIL, ADMIN_ID, "상담원", UserRole.ADMIN);
        Member owner = mock(Member.class);
        lenient().when(owner.getId()).thenReturn(USER_ID);
        lenient().when(owner.getNickname()).thenReturn("고객");
        when(chatRoomRepository.claim(ROOM_ID, admin)).thenReturn(1);
        ChatRoom claimedRoom = stubRoom(owner, admin, ChatRoomStatus.ACTIVE);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(claimedRoom));
        when(chatMessageRepository.countByChatRoomIdAndSenderIdNotAndIsReadFalse(ROOM_ID, ADMIN_ID))
                .thenReturn(0L);

        // when
        ChatRoomResponseDto result = chatRoomService.claimRoom(ADMIN_EMAIL, ROOM_ID);

        // then
        assertThat(result.status()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(result.adminId()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<ChatMessageResponseDto> captor = ArgumentCaptor.forClass(ChatMessageResponseDto.class);
        verify(redisChatPublisher).publish(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.SYSTEM);
    }

    @Test
    @DisplayName("상담 수락 실패 - 이미 다른 상담원이 배정된 방이면 CHATROOM_ALREADY_CLAIMED 예외가 발생한다.")
    void claimRoom_AlreadyClaimed_ThrowsChatroomAlreadyClaimed() {
        // given
        Member admin = stubMember(ADMIN_EMAIL, ADMIN_ID, "상담원", UserRole.ADMIN);
        when(chatRoomRepository.claim(ROOM_ID, admin)).thenReturn(0);
        ChatRoom activeRoom = stubRoom(mock(Member.class), null, ChatRoomStatus.ACTIVE);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(activeRoom));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.claimRoom(ADMIN_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHATROOM_ALREADY_CLAIMED);
        verify(redisChatPublisher, never()).publish(anyLong(), any(ChatMessageResponseDto.class));
    }

    @Test
    @DisplayName("상담 수락 실패 - 종료된 방이면 CHATROOM_ALREADY_CLOSED 예외가 발생한다.")
    void claimRoom_ClosedRoom_ThrowsChatroomAlreadyClosed() {
        // given
        Member admin = stubMember(ADMIN_EMAIL, ADMIN_ID, "상담원", UserRole.ADMIN);
        when(chatRoomRepository.claim(ROOM_ID, admin)).thenReturn(0);
        ChatRoom closedRoom = stubRoom(mock(Member.class), null, ChatRoomStatus.CLOSED);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(closedRoom));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.claimRoom(ADMIN_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHATROOM_ALREADY_CLOSED);
    }

    // ================= [ 상담 종료 ] =================

    @Test
    @DisplayName("상담 종료 성공 - 방 주인이 종료하면 close 후 SYSTEM 메시지를 발행한다.")
    void closeRoom_ByOwner_ClosesAndPublishes() {
        // given
        stubMember(USER_EMAIL, USER_ID, "고객", UserRole.USER);
        ChatRoom room = stubRoom(mock(Member.class), null, ChatRoomStatus.ACTIVE);
        when(room.isParticipant(USER_ID)).thenReturn(true);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // when
        chatRoomService.closeRoom(USER_EMAIL, ROOM_ID);

        // then
        verify(room).close();
        ArgumentCaptor<ChatMessageResponseDto> captor = ArgumentCaptor.forClass(ChatMessageResponseDto.class);
        verify(redisChatPublisher).publish(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.SYSTEM);
    }

    @Test
    @DisplayName("상담 종료 실패 - 참여자가 아니면 NOT_CHATROOM_PARTICIPANT 예외가 발생한다.")
    void closeRoom_NotParticipant_ThrowsNotChatroomParticipant() {
        // given
        stubMember(USER_EMAIL, USER_ID, "타인", UserRole.USER);
        ChatRoom room = stubRoom(mock(Member.class), null, ChatRoomStatus.ACTIVE);
        when(room.isParticipant(USER_ID)).thenReturn(false);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.closeRoom(USER_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        verify(room, never()).close();
    }

    @Test
    @DisplayName("상담 종료 실패 - 이미 종료된 방이면 CHATROOM_ALREADY_CLOSED 예외가 발생한다.")
    void closeRoom_AlreadyClosed_ThrowsChatroomAlreadyClosed() {
        // given
        stubMember(USER_EMAIL, USER_ID, "고객", UserRole.USER);
        ChatRoom room = stubRoom(mock(Member.class), null, ChatRoomStatus.CLOSED);
        when(room.isParticipant(USER_ID)).thenReturn(true);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.closeRoom(USER_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHATROOM_ALREADY_CLOSED);
    }

    // ================= [ 이력 조회 / 구독 검증 ] =================

    @Test
    @DisplayName("이력 조회 실패 - 참여자도 ADMIN도 아니면 NOT_CHATROOM_PARTICIPANT 예외가 발생한다.")
    void getMessages_Stranger_ThrowsNotChatroomParticipant() {
        // given
        stubMember(USER_EMAIL, 2L, "타인", UserRole.USER);
        ChatRoom room = stubRoom(mock(Member.class), null, ChatRoomStatus.ACTIVE);
        when(room.isParticipant(2L)).thenReturn(false);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.getMessages(USER_EMAIL, ROOM_ID, org.springframework.data.domain.Pageable.unpaged()));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHATROOM_PARTICIPANT);
    }

    @Test
    @DisplayName("구독 검증 통과 - 방 참여자가 아니어도 ADMIN이면 구독할 수 있다(수락 전 미리보기).")
    void validateSubscription_AdminRole_Passes() {
        // given
        stubMember(ADMIN_EMAIL, ADMIN_ID, "상담원", UserRole.ADMIN);
        ChatRoom room = stubRoom(mock(Member.class), null, ChatRoomStatus.WAITING);
        when(room.isParticipant(ADMIN_ID)).thenReturn(false);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // when & then
        assertDoesNotThrow(() -> chatRoomService.validateSubscription(ADMIN_EMAIL, ROOM_ID));
    }

    @Test
    @DisplayName("구독 검증 실패 - 참여자도 ADMIN도 아닌 사용자는 NOT_CHATROOM_PARTICIPANT 예외가 발생한다.")
    void validateSubscription_Stranger_ThrowsNotChatroomParticipant() {
        // given
        stubMember(USER_EMAIL, 2L, "타인", UserRole.USER);
        ChatRoom room = stubRoom(mock(Member.class), null, ChatRoomStatus.ACTIVE);
        when(room.isParticipant(2L)).thenReturn(false);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.validateSubscription(USER_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CHATROOM_PARTICIPANT);
    }

    @Test
    @DisplayName("구독 검증 실패 - 존재하지 않는 방이면 CHATROOM_NOT_FOUND 예외가 발생한다.")
    void validateSubscription_RoomNotFound_ThrowsChatroomNotFound() {
        // given
        stubMember(USER_EMAIL, USER_ID, "고객", UserRole.USER);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatRoomService.validateSubscription(USER_EMAIL, ROOM_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHATROOM_NOT_FOUND);
    }
}
