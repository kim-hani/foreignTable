package OneTwo.SmartWaiting.domain.store.entity;

public record HourlyStatVo(
     int hour,
     int avgTeams,
     int avgWaitMin
) { }
