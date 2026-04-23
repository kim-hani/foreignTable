package OneTwo.SmartWaiting.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FcmService {

    /**
     * @param targetToken 유저의 핸드폰 토큰
     * @param title       알림 제목
     * @param body        알림 내용
     * @param storeId     이동할 식당 ID (클릭 시 이동용)
     */
    public void sendReviewRequestPush(String targetToken, String title, String body, Long storeId) {
        if (targetToken == null || targetToken.isEmpty()) {
            return; // 토큰이 없으면 안 보냄
        }

        // 프론트엔드에서 알림을 눌렀을 때 특정 식당 리뷰 창으로 이동할 수 있도록 데이터를 같이 넣어줌
        Message message = Message.builder()
                .setToken(targetToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("type", "REVIEW_REQUEST") // 알림 종류
                .putData("storeId", String.valueOf(storeId)) // 이동할 식당 ID
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
            log.info("리뷰 푸시 알림 발송 성공! (Token: {})", targetToken);
        } catch (Exception e) {
            log.error("푸시 알림 발송 실패", e);
        }
    }
}