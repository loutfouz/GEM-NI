package nodebox.client;

public class AnimationManager {
	private double animationFrame = 1;
	private AnimationTimer animationTimer;

	private AnimationBar animationBar;
	private NodeBoxDocument document;

	AnimationManager(NodeBoxDocument document, AnimationBar animationBar){
		this.document = document;
		this.animationBar = animationBar;
        animationTimer = new AnimationTimer(this);

	}
    //// Animation ////
	public double getAnimationFrame(){
		return animationFrame;
	}
	
    public double getFrame() {
        return animationFrame;
    }

    public void nextFrame() {
        setFrame(getFrame() + 1);
    }


    public void setFrame(double frame) {
        this.animationFrame = frame;

        animationBar.setFrame(frame);
        document.requestRender();
    }

    public void toggleAnimation() {
        animationBar.toggleAnimation();
    }

    public void doRewind() {
        animationBar.rewindAnimation();
    }

    public void playAnimation() {
        animationTimer.start();
    }

    public void stopAnimation() {
        animationTimer.stop();
    }

    public void rewindAnimation() {
        stopAnimation();
        document.resetRenderResults();
        setFrame(1);
    }
}
