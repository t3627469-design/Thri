package baritone.api.process;
public interface ICustomGoalProcess {
    boolean isActive();
    void setGoalAndPath(baritone.api.pathing.goals.Goal goal);
}
