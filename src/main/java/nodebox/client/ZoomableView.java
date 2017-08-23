package nodebox.client;

import nodebox.ui.Platform;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.IOException;

public abstract class ZoomableView extends JComponent {

    private static Cursor defaultCursor, panCursor;
    private final double minZoom, maxZoom;
    // View state
    private double viewX, viewY, viewScale = 1;
    private transient AffineTransform viewTransform = null;
    private transient AffineTransform inverseViewTransform = null;
    // Interaction state
    private boolean isSpacePressed = false;
    private boolean isPanning = false;
    private Point2D dragStartPoint;

    static {
        Image panCursorImage;

        try {
            if (Platform.onWindows())
                panCursorImage = ImageIO.read(NetworkView.class.getResourceAsStream("/view-cursor-pan-32.png"));
            else
                panCursorImage = ImageIO.read(NetworkView.class.getResourceAsStream("/view-cursor-pan.png"));
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            panCursor = toolkit.createCustomCursor(panCursorImage, new Point(0, 0), "PanCursor");
            defaultCursor = Cursor.getDefaultCursor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ZoomableView(double minZoom, double maxZoom) {
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        setFocusable(true);
        addKeyListener(new KeyHandler());
        addMouseListener(new MouseHandler());
        addMouseMotionListener(new MouseMotionHandler());
        addMouseWheelListener(new MouseWheelHandler(this));
        final FocusHandler fh = new FocusHandler();
        addFocusListener(fh);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
            	try {
            		SwingUtilities.getWindowAncestor(ZoomableView.this).addWindowFocusListener(fh);
            	} catch(NullPointerException e) {} // just eat
            }
        });
    }

    public double getViewX() {
        return viewX;
    }

    public void setViewPosition(double x, double y) {
        setViewX(x);
        setViewY(y);
        repaint();
    }

    public double getViewY() {
        return viewY;
    }

    public double getViewScale() {
        return viewScale;
    }

    public boolean isSpacePressed() {
        return isSpacePressed;
    }

    public boolean isDragTrigger(MouseEvent e) {
        return isSpacePressed();
    }

    public boolean isPanning() {
        return isPanning;
    }

    private Point2D minPoint(Point2D a, Point2D b) {
        return new Point2D.Double(a.getX() - b.getX(), a.getY() - b.getY());
    }

    //this function is overriden on Viewer and NetworkView to allow synchronized zooming across alternatives
    public void setViewTransform(double viewX, double viewY, double viewScale) {
    	_setViewTransform( viewX,  viewY, viewScale, this);
    }
    //view transport for geometry
    public void setViewTransformNonGeometry(double viewX, double viewY, double viewScale) {
    	//_setViewTransform( viewX,  viewY, viewScale, this);
        this.viewX = viewX;
        this.viewY = viewY;
        this.viewScale = viewScale;
        //onViewTransformChanged(viewX, viewY, viewScale);
        this.viewTransform = null;
        this.inverseViewTransform = null;
        repaint();

    }


	protected static void _setViewTransform(double viewX, double viewY,
			double viewScale, ZoomableView zoomableView) {
		zoomableView.setViewX(viewX);
		zoomableView.setViewY(viewY);
		zoomableView.setViewScale(viewScale);
		zoomableView.setViewTransform(null);
		zoomableView.setInverseViewTransform(null);
		zoomableView.repaint();
	}

    public AffineTransform getViewTransform() {
        if (viewTransform == null) {
            viewTransform = new AffineTransform();
            viewTransform.translate(getViewX(), getViewY());
            viewTransform.scale(getViewScale(), getViewScale());
        }
        return viewTransform;
    }

    //// View Transform ////

    public AffineTransform getInverseViewTransform() {
        if (inverseViewTransform == null) {
            try {
                inverseViewTransform = getViewTransform().createInverse();
            } catch (NoninvertibleTransformException e) {
                inverseViewTransform = new AffineTransform();
            }
        }
        return inverseViewTransform;
    }

    public void resetViewTransform() {
            setViewTransform(0, 0, 1);
    }

    public Point2D inverseViewTransformPoint(Point p) {
        Point2D pt = new Point2D.Double(p.getX(), p.getY());
        return getInverseViewTransform().transform(pt, null);
    }

    public void zoom(double scaleDelta, double x, double y) {
        if (!isVisible()) return;
        double currentScale = getViewScale();
        double newScale = currentScale * scaleDelta;
        if (newScale < minZoom) {
            scaleDelta = minZoom / getViewScale();
        } else if (newScale > maxZoom) {
            scaleDelta = maxZoom / getViewScale();
        }
        double vx = getViewX() - (x - getViewX()) * (scaleDelta - 1);
        double vy = getViewY() - (y - getViewY()) * (scaleDelta - 1);
        setViewTransform(vx, vy, getViewScale() * scaleDelta);
    }

	public abstract void centerAndZoom(boolean relativeToAll); //implemented in both network and viewer

    public void setViewX(double viewX) {
		this.viewX = viewX;
	}

	public void setViewY(double viewY) {
		this.viewY = viewY;
	}

	public void setViewScale(double viewScale) {
		this.viewScale = viewScale;
	}

	public void setViewTransform(AffineTransform viewTransform) {
		this.viewTransform = viewTransform;
	}

	public void setInverseViewTransform(AffineTransform inverseViewTransform) {
		this.inverseViewTransform = inverseViewTransform;
	}

	private class KeyHandler extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int keyCode = e.getKeyCode();
            if (keyCode == KeyEvent.VK_SPACE) {
                isSpacePressed = true;
                setCursor(panCursor);
            }

        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                isSpacePressed = false;
                setCursor(defaultCursor);
            }
        }
    }

    private class MouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) return;
            // If the space bar and mouse is pressed, we're getting ready to pan the view.
            if (isSpacePressed) {
                // When panning the view use the original mouse point, not the one affected by the view transform.
                dragStartPoint = e.getPoint();
                isPanning = true;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            isPanning = false;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            grabFocus();
        }
    }

    private class MouseMotionHandler extends MouseMotionAdapter {
        @Override
        public void mouseDragged(MouseEvent e) {
            if (isPanning()) {
                // When panning the view use the original mouse point, not the one affected by the view transform.
                Point2D offset = minPoint(e.getPoint(), dragStartPoint);
                setViewTransform(getViewX() + offset.getX(), getViewY() + offset.getY(), getViewScale());
                dragStartPoint = e.getPoint();
            }
        }
    }

    private class MouseWheelHandler implements MouseWheelListener {

    	private ZoomableView zoomableView;
        public MouseWheelHandler(ZoomableView zoomableView) {
        	this.zoomableView = zoomableView;
        }

		@Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            double scaleDelta = 1.0 - (e.getWheelRotation() / 10.0);
            zoom(scaleDelta, e.getX(), e.getY());
        }
    }

    private class FocusHandler implements WindowFocusListener, FocusListener {
        @Override
        public void windowLostFocus(WindowEvent e) {
            isSpacePressed = false;
            isPanning = false;
            setCursor(defaultCursor);
        }

        @Override
        public void windowGainedFocus(WindowEvent e) {
        }

        @Override
        public void focusGained(FocusEvent e) {
        }

        @Override
        public void focusLost(FocusEvent e) {
            isSpacePressed = false;
            setCursor(defaultCursor);
        }
    }
}
