package OneTwo.SmartWaiting.domain.store.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemVo implements Serializable {
    private String name;
    private int price;
    private String description;
}
