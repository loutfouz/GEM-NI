package nodebox.client;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AnimationTimer implements ActionListener {

    private AnimationManager animationManager;
    private Timer timer;

    public AnimationTimer(AnimationManager animationManager) {
        this.animationManager = animationManager;
        timer = new Timer(1000 / 60, this);
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    public void actionPerformed(ActionEvent e) {
        // Timer has fired.
    	animationManager.nextFrame();
    }
}
