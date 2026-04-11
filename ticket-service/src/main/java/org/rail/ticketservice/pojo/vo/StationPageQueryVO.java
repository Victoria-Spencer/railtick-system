package org.rail.ticketservice.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StationPageQueryVO {

    private String name;
    private String code;
    private String spell;
}
