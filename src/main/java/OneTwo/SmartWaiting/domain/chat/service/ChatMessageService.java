package OneTwo.SmartWaiting.domain.chat.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageResponseDto;
import OneTwo.SmartWaiting.domain.chat.dto.ChatMessageSendRequestDto;
import OneTwo.SmartWaiting.domain.chat.entity.ChatMessage;
import OneTwo.SmartWaiting.domain.chat.entity.ChatRoom;
import OneTwo.SmartWaiting.domain.chat.enums.ChatRoomStatus;
import OneTwo.SmartWaiting.domain.chat.redis.RedisChatPublisher;
import OneTwo.SmartWaiting.domain.chat.repository.ChatMessageRepository;
import OneTwo.SmartWaiting.domain.chat.repository.ChatRoomRepository;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 메시지 전송/읽음 처리.
 *
 * <p>전송은 "검증 → DB 저장 → Redis 발행" 순서 — 브로드캐스트는 저장된 내용 기준이므로
 * 수신자가 이력 조회와 다른 내용을 볼 일이 없다. Redis 발행이 트랜잭션 커밋 전에
 * 일어나는 트레이드오프(롤백 시 유령 브로드캐스트 가능)는 MVP에서 수용하고,
 * 매뉴얼 벡터 동기화(#44)와 같은 AFTER_COMMIT 이벤트 방식으로의 이관 여지를 남긴다.
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final RedisChatPublisher redisChatPublisher;

    @Transactional
    public void sendMessage(String email, Long roomId, ChatMessageSendRequestDto request) {
        Member sender = findMemberOrThrow(email);
        ChatRoom room = findRoomOrThrow(roomId);

        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLOSED);
        }
        if (!room.isParticipant(sender.getId())) {
            throw new BusinessException(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        }

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .content(request.content())
                .build());

        redisChatPublisher.publish(roomId, ChatMessageResponseDto.from(saved));
    }

    /**
     * 방 단위 읽음 처리 — 상대방이 보낸 미읽음 메시지를 벌크 UPDATE로 일괄 처리하고,
     * 처리된 것이 있으면 READ 영수증을 발행해 상대방 화면의 "읽음" 표시를 갱신한다.
     */
    @Transactional
    public void markAsRead(String email, Long roomId) {
        Member reader = findMemberOrThrow(email);
        ChatRoom room = findRoomOrThrow(roomId);

        if (!room.isParticipant(reader.getId())) {
            throw new BusinessException(ErrorCode.NOT_CHATROOM_PARTICIPANT);
        }

        int updated = chatMessageRepository.markAllAsRead(roomId, reader.getId());
        if (updated > 0) {
            redisChatPublisher.publish(roomId, ChatMessageResponseDto.read(roomId, reader.getId()));
        }
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
