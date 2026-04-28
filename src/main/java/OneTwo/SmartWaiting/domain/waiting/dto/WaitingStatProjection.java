package OneTwo.SmartWaiting.domain.waiting.dto;

public interface WaitingStatProjection {
    Integer getDayOfWeek(); // 요일 (1=월요일 ~ 7=일요일)
    Integer getHourOfDay(); // 시간 (0~23)
    Double getAvgTeams();   // 평균 대기 팀
    Double getAvgWaitMin(); // 평균 대기 시간
}