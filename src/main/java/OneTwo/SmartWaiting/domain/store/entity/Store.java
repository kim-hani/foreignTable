package OneTwo.SmartWaiting.domain.store.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
public class Store extends BaseEntity {

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String category;

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

    public Store(Long ownerId, String name, String phone,String category, Integer averageWaiting,Point location,
                       Map<String, String> businessHours, List<MenuItemVo> menuItems) {
        this.ownerId = ownerId;
        this.name = name;
        this.phone = phone;
        this.category = category;
        this.averageWaiting = averageWaiting;
        this.location = location;
        this.businessHours = businessHours;
        this.menuItems = menuItems;
    }
}
