package OneTwo.SmartWaiting.common.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column
    private Instant updatedAt;

    @LastModifiedDate
    @Column
    private Instant deletedAt;

    @Column(nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

    public Long softDelete() {
        this.isDeleted = true;
        return this.id;
    }

    public void restore(){
        this.isDeleted = false;
    }
}
