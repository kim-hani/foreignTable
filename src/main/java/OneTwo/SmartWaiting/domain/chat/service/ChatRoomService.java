package OneTwo.SmartWaiting.domain.chat.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import OneTwo.SmartWaiting.domain.chat.dto.ChatRoomResponseDto;
import OneTwo.SmartWaiting.domain.chat.entity.ChatRoom;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.chat.redis.RedisChatPublisher;
import OneTwo.SmartWaiting.domain.chat.repository.ChatMessageRepository;
import OneTwo.SmartWaiting.domain.chat.repository.ChatRoomRepository;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상담 채팅방 수명주기 관리 — 생성(에스컬레이션) → 상담원 수락(claim) → 종료.
 *
 * <p>AI 챗봇이 needsAgent=true를 반환하면 프론트가 "상담원 연결" 버튼을 노출하고,
 * 사용자가 클릭할 때 createRoom이 호출된다(챗봇 코드와는 결합 없음).
 * 수락/종료 시 SYSTEM 이벤트를 Redis로 발행해 상대방 화면에 상태 전환을 실시간 반영한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private static final String SYSTEM_CLAIMED = "상담원이 연결되었습니다.";
    private static final String SYSTEM_CLOSED = "상담이 종료되었습니다.";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final RedisChatPublisher redisChatPublisher;

    /** 상담 요청 — 이미 열린 방(WAITING/ACTIVE)이 있으면 그대로 반환한다(멱등). */
    @Transactional
    public ChatRoomResponseDto createRoom(String email) {
        Member member = findMemberOrThrow(email);

        return chatRoomRepository
                .findFirstByMemberIdAndStatusNotAndIsDeletedFalse(member.getId(), ChatRoomStatus.CLOSED)
                .map(room -> toDto(room, member.getId()))
                .orElseGet(() -> {
                    ChatRoom room = ChatRoom.builder()
                            .member(member)
                            .status(ChatRoomStatus.WAITING)
                            .build();
                    return ChatRoomResponseDto.from(chatRoomRepository.save(room), 0);
                });
    }

    /** 내 열린 상담 방 조회 (미읽음 수 포함). */
    public ChatRoomResponseDto getMyOpenRoom(String email) {
        Member member = findMemberOrThrow(email);
        ChatRoom room = chatRoomRepository
                .findFirstByMemberIdAndStatusNotAndIsDeletedFalse(member.getId(), ChatRoomStatus.CLOSED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
        return toDto(room, member.getId());
    }

    /** 상담원 콘솔용 상태별 방 목록 (오래된 요청부터 — 선착순 응대). */
    public Slice<ChatRoomResponseDto> getRoomsByStatus(String adminEmail, ChatRoomStatus status, Pageable pageable) {
        Member admin = findMemberOrThrow(adminEmail);
        return chatRoomRepository.findAllByStatusAndIsDeletedFalseOrderByCreatedAtAsc(status, pageable)
                .map(room -> toDto(room, admin.getId()));
    }

    /**
     * 상담 수락 — 조건부 UPDATE(WAITING일 때만)로 동시 수락 경합을 원자적으로 처리한다.
     * 갱신된 행이 없으면 방이 없거나(CH001), 종료됐거나(CH004), 이미 배정된(CH003) 것이다.
     */
    @Transactional
    public ChatRoomResponseDto claimRoom(String adminEmail, Long roomId) {
        Member admin = findMemberOrThrow(adminEmail);

        int updated = chatRoomRepository.claim(roomId, admin);
        if (updated == 0) {
            ChatRoom room = findRoomOrThrow(roomId);
            if (room.getStatus() == ChatRoomStatus.CLOSED) {
                throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLOSED);
            }
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLAIMED);
        }

        redisChatPublisher.publish(roomId, ChatMessageResponseDto.system(roomId, SYSTEM_CLAIMED));
        return toDto(findRoomOrThrow(roomId), admin.getId());
    }

    /** 상담 종료 — 방 주인(USER)의 대기 취소 겸용, 담당 상담원도 가능. */
    @Transactional
    public void closeRoom(String email, Long roomId) {
        Member member = findMemberOrThrow(email);
        ChatRoom room = findRoomOrThrow(roomId);

        if (!room.isParticipant(member.getId())) {
            throw new BusinessException(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        }
        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLOSED);
        }

        room.close();
        redisChatPublisher.publish(roomId, ChatMessageResponseDto.system(roomId, SYSTEM_CLOSED));
    }

    /** 메시지 이력 조회 — 참여자 또는 ADMIN(수락 전 미리보기)만 허용. */
    public Slice<ChatMessageResponseDto> getMessages(String email, Long roomId, Pageable pageable) {
        Member member = findMemberOrThrow(email);
        ChatRoom room = findRoomOrThrow(roomId);
        validateAccess(room, member);

        return chatMessageRepository.findAllByChatRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .map(ChatMessageResponseDto::from);
    }

    /**
     * STOMP SUBSCRIBE 검증(StompAuthChannelInterceptor에서 호출) —
     * roomId만 알면 타인의 상담 내용을 도청할 수 있으므로 구독 시점에 차단한다.
     */
    public void validateSubscription(String email, Long roomId) {
        Member member = findMemberOrThrow(email);
        ChatRoom room = findRoomOrThrow(roomId);
        validateAccess(room, member);
    }

    /** 방 주인/담당 상담원이 아니어도 ADMIN이면 통과 — 수락 전 상담 내용 확인이 가능해야 하므로. */
    private void validateAccess(ChatRoom room, Member member) {
        if (!room.isParticipant(member.getId()) && member.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        }
    }

    private ChatRoomResponseDto toDto(ChatRoom room, Long viewerId) {
        long unreadCount = chatMessageRepository
                .countByChatRoomIdAndSenderIdNotAndIsReadFalse(room.getId(), viewerId);
        return ChatRoomResponseDto.from(room, unreadCount);
    }

    private Member findMemberOrThrow(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private ChatRoom findRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .filter(room -> !room.getIsDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
    }
}
