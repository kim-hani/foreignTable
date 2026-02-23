package OneTwo.SmartWaiting.domain.store.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "store")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Store extends BaseEntity {

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreCategory category;

    @Column(nullable = false)
    @Builder.Default
    private Integer averageWaiting = 10;

    // [PostGis] 위치 정보 매핑
    // SRID 4326 = WGS 84(GPS 좌표계)
    @Column(columnDefinition = "geometry(point, 4326)")
    private Point location;

    // [JSONB] 영업 시간 매핑 (Map<String, String>)
    // ex: {"mon": "10:00-22:00", "tue": "OFF"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "jsonb")
    private Map<String, String> businessHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "menuItems",columnDefinition = "jsonb")
    private List<MenuItemVo> menuItems;

    public void updateInfo(String name, String phone, StoreCategory category, Integer averageWaiting,
                           Point location, Map<String, String> businessHours, List<MenuItemVo> menuItems) {
        this.name = name;
        this.phone = phone;
        this.category = category;
        this.averageWaiting = averageWaiting != null ? averageWaiting : this.averageWaiting;
        this.location = location; // 위치 업데이트
        this.businessHours = businessHours;
        this.menuItems = menuItems;
    }
}
