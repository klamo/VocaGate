package com.vocagate.desktop.app;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

public final class DesktopLineIcon implements Icon {
    public enum Type {
        MIC,
        CAMERA,
        VIDEO,
        LINK,
        NETWORK,
        MONITOR,
        WIFI,
        KEYBOARD,
        ACTIVITY,
        SERVER,
        SIGNAL,
        CLOCK,
        USER,
        STAR,
        PLAY,
        STOP,
        REFRESH,
        COPY,
        LOCK
    }

    private final Type type;
    private final int size;
    private final Color color;

    public DesktopLineIcon(Type type, int size, Color color) {
        this.type = type;
        this.size = size;
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.7f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (type) {
            case MIC:
                mic(g);
                break;
            case CAMERA:
                camera(g);
                break;
            case VIDEO:
                video(g);
                break;
            case LINK:
                link(g);
                break;
            case NETWORK:
                network(g);
                break;
            case MONITOR:
                monitor(g);
                break;
            case WIFI:
                wifi(g);
                break;
            case KEYBOARD:
                keyboard(g);
                break;
            case ACTIVITY:
                activity(g);
                break;
            case SERVER:
                server(g);
                break;
            case SIGNAL:
                signal(g);
                break;
            case CLOCK:
                clock(g);
                break;
            case USER:
                user(g);
                break;
            case STAR:
                star(g);
                break;
            case PLAY:
                play(g);
                break;
            case STOP:
                stop(g);
                break;
            case REFRESH:
                refresh(g);
                break;
            case COPY:
                copy(g);
                break;
            case LOCK:
                lock(g);
                break;
            default:
                break;
        }
        g.dispose();
    }

    private double p(double value) {
        return size * value;
    }

    private void mic(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.36), p(0.14), p(0.28), p(0.48), p(0.16), p(0.16)));
        g.draw(new Arc2D.Double(p(0.22), p(0.36), p(0.56), p(0.36), 180, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(p(0.50), p(0.78), p(0.50), p(0.90)));
        g.draw(new Line2D.Double(p(0.36), p(0.90), p(0.64), p(0.90)));
    }

    private void camera(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.14), p(0.28), p(0.72), p(0.52), p(0.10), p(0.10)));
        g.draw(new RoundRectangle2D.Double(p(0.24), p(0.18), p(0.20), p(0.16), p(0.06), p(0.06)));
        g.draw(new Ellipse2D.Double(p(0.39), p(0.41), p(0.22), p(0.22)));
    }

    private void video(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.12), p(0.28), p(0.52), p(0.44), p(0.10), p(0.10)));
        Path2D lens = new Path2D.Double();
        lens.moveTo(p(0.64), p(0.42));
        lens.lineTo(p(0.86), p(0.30));
        lens.lineTo(p(0.86), p(0.70));
        lens.lineTo(p(0.64), p(0.58));
        g.draw(lens);
    }

    private void link(Graphics2D g) {
        g.draw(new Arc2D.Double(p(0.10), p(0.32), p(0.44), p(0.36), 95, 220, Arc2D.OPEN));
        g.draw(new Arc2D.Double(p(0.46), p(0.32), p(0.44), p(0.36), -85, 220, Arc2D.OPEN));
        g.draw(new Line2D.Double(p(0.38), p(0.57), p(0.62), p(0.43)));
    }

    private void network(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.42), p(0.12), p(0.16), p(0.16), p(0.04), p(0.04)));
        g.draw(new RoundRectangle2D.Double(p(0.12), p(0.72), p(0.16), p(0.16), p(0.04), p(0.04)));
        g.draw(new RoundRectangle2D.Double(p(0.42), p(0.72), p(0.16), p(0.16), p(0.04), p(0.04)));
        g.draw(new RoundRectangle2D.Double(p(0.72), p(0.72), p(0.16), p(0.16), p(0.04), p(0.04)));
        g.draw(new Line2D.Double(p(0.50), p(0.28), p(0.50), p(0.58)));
        g.draw(new Line2D.Double(p(0.20), p(0.58), p(0.80), p(0.58)));
        g.draw(new Line2D.Double(p(0.20), p(0.58), p(0.20), p(0.72)));
        g.draw(new Line2D.Double(p(0.50), p(0.58), p(0.50), p(0.72)));
        g.draw(new Line2D.Double(p(0.80), p(0.58), p(0.80), p(0.72)));
    }

    private void monitor(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.14), p(0.20), p(0.72), p(0.52), p(0.06), p(0.06)));
        g.draw(new Line2D.Double(p(0.40), p(0.84), p(0.60), p(0.84)));
        g.draw(new Line2D.Double(p(0.50), p(0.72), p(0.50), p(0.84)));
    }

    private void wifi(Graphics2D g) {
        g.draw(new Arc2D.Double(p(0.12), p(0.32), p(0.76), p(0.60), 36, 108, Arc2D.OPEN));
        g.draw(new Arc2D.Double(p(0.26), p(0.48), p(0.48), p(0.38), 40, 100, Arc2D.OPEN));
        g.draw(new Arc2D.Double(p(0.40), p(0.62), p(0.20), p(0.16), 45, 90, Arc2D.OPEN));
        g.fill(new Ellipse2D.Double(p(0.46), p(0.82), p(0.08), p(0.08)));
    }

    private void keyboard(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.12), p(0.24), p(0.76), p(0.52), p(0.08), p(0.08)));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 5; col++) {
                g.fill(new RoundRectangle2D.Double(p(0.22 + col * 0.12), p(0.38 + row * 0.14), p(0.035), p(0.035), 2, 2));
            }
        }
    }

    private void activity(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(p(0.10), p(0.54));
        path.lineTo(p(0.28), p(0.54));
        path.lineTo(p(0.38), p(0.24));
        path.lineTo(p(0.52), p(0.78));
        path.lineTo(p(0.64), p(0.42));
        path.lineTo(p(0.74), p(0.54));
        path.lineTo(p(0.90), p(0.54));
        g.draw(path);
    }

    private void server(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.18), p(0.22), p(0.64), p(0.20), p(0.06), p(0.06)));
        g.draw(new RoundRectangle2D.Double(p(0.18), p(0.58), p(0.64), p(0.20), p(0.06), p(0.06)));
        g.draw(new Line2D.Double(p(0.28), p(0.32), p(0.38), p(0.32)));
        g.draw(new Line2D.Double(p(0.28), p(0.68), p(0.38), p(0.68)));
    }

    private void signal(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.16), p(0.62), p(0.10), p(0.24), p(0.04), p(0.04)));
        g.draw(new RoundRectangle2D.Double(p(0.36), p(0.46), p(0.10), p(0.40), p(0.04), p(0.04)));
        g.draw(new RoundRectangle2D.Double(p(0.56), p(0.30), p(0.10), p(0.56), p(0.04), p(0.04)));
        g.draw(new RoundRectangle2D.Double(p(0.76), p(0.16), p(0.10), p(0.70), p(0.04), p(0.04)));
    }

    private void clock(Graphics2D g) {
        g.draw(new Ellipse2D.Double(p(0.16), p(0.16), p(0.68), p(0.68)));
        g.draw(new Line2D.Double(p(0.50), p(0.50), p(0.50), p(0.30)));
        g.draw(new Line2D.Double(p(0.50), p(0.50), p(0.64), p(0.60)));
    }

    private void user(Graphics2D g) {
        g.draw(new Ellipse2D.Double(p(0.36), p(0.18), p(0.28), p(0.28)));
        g.draw(new Arc2D.Double(p(0.20), p(0.46), p(0.60), p(0.42), 20, 140, Arc2D.OPEN));
        g.draw(new Ellipse2D.Double(p(0.14), p(0.14), p(0.72), p(0.72)));
    }

    private void star(Graphics2D g) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double radius = i % 2 == 0 ? p(0.36) : p(0.16);
            double cx = p(0.50) + Math.cos(angle) * radius;
            double cy = p(0.52) + Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(cx, cy);
            } else {
                path.lineTo(cx, cy);
            }
        }
        path.closePath();
        g.draw(path);
    }

    private void play(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(p(0.32), p(0.22));
        path.lineTo(p(0.76), p(0.50));
        path.lineTo(p(0.32), p(0.78));
        path.closePath();
        g.fill(path);
    }

    private void stop(Graphics2D g) {
        g.fill(new RoundRectangle2D.Double(p(0.30), p(0.30), p(0.40), p(0.40), p(0.06), p(0.06)));
    }

    private void refresh(Graphics2D g) {
        g.draw(new Arc2D.Double(p(0.20), p(0.22), p(0.58), p(0.58), 40, 260, Arc2D.OPEN));
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(p(0.72), p(0.22));
        arrow.lineTo(p(0.82), p(0.22));
        arrow.lineTo(p(0.78), p(0.34));
        g.draw(arrow);
    }

    private void copy(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.28), p(0.18), p(0.42), p(0.52), p(0.06), p(0.06)));
        g.draw(new RoundRectangle2D.Double(p(0.40), p(0.30), p(0.42), p(0.52), p(0.06), p(0.06)));
    }

    private void lock(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(p(0.26), p(0.44), p(0.48), p(0.38), p(0.08), p(0.08)));
        g.draw(new Arc2D.Double(p(0.34), p(0.20), p(0.32), p(0.36), 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(p(0.50), p(0.56), p(0.50), p(0.68)));
    }
}
