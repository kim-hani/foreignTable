package OneTwo.SmartWaiting.domain.waiting.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.waiting.enums.WaitingStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor (access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Waiting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private Integer headCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitingStatus status;

    @Column(nullable = false)
    private Integer queueNumber;

    private Integer expectedWaitMin;

    public void changeStatus(WaitingStatus status) {
        this.status = status;
    }
}
