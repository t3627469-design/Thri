package baritone.api;
public interface IBaritone {
    baritone.api.process.ICustomGoalProcess getCustomGoalProcess();
    baritone.api.behavior.IPathingBehavior getPathingBehavior();
    baritone.api.utils.IInputOverrideHandler getInputOverrideHandler();
}
