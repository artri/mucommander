package com.mucommander.ui.dnd;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.io.Serializable;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class DragAndDropTest implements Serializable {

    public static void main(String[] args) {
        new DragAndDropTest();;
    }

    public DragAndDropTest() {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                    ex.printStackTrace();
                }

                JFrame frame = new JFrame("Testing");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(new TestPane());
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    public class TestPane extends JPanel {

        public TestPane() {
            setLayout(new GridLayout(1, 2));

            JPanel container = new JPanel();
        	container.setBackground(Color.GREEN);
            container.setPreferredSize(new Dimension(100, 100));            

            DragPane drag = new DragPane();
            container.add(drag);

            add(container);
            add(new DropPane());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 200);
        }

    }

    public class DragPane extends JPanel {
        private DragGestureHandler dragGestureHandler;

        public DragPane() {
            System.out.println("DragPane = " + this.hashCode());
            setBackground(Color.RED);
            dragGestureHandler = new DragGestureHandler(this);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(50, 50);
        }
    }
    
    public class DropPane extends JPanel {
        private DropTargetHandler dropHandler;

        public DropPane() {
            setBackground(Color.BLUE);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 100);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            dropHandler = new DropTargetHandler(this, DragPane.class);
        }

        @Override
        public void removeNotify() {
            super.removeNotify();
            dropHandler.release();
        }
    }
}