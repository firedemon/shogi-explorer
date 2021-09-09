package com.chadfield.shogiexplorer.main;

import com.chadfield.shogiexplorer.objects.Engine;
import com.chadfield.shogiexplorer.objects.EngineOption;
import com.chadfield.shogiexplorer.objects.Game;
import com.chadfield.shogiexplorer.objects.Position;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author Stephen Chadfield <stephen@chadfield.com>
 */
public class GameAnalyser {

    Process process;
    OutputStream stdin;
    InputStream stdout;
    BufferedReader bufferedReader;

    public void analyse(Game game, Engine engine, JList moveList, JTable analysisTable, DefaultTableModel analysisTableModel) throws IOException {
        initializeEngine(engine);
        initiateUSIProtocol();
        setOptions(engine);
        getReady();
        String engineMove = null;
        String sfen = null;
        String lastSFEN = null;
        int count = 0;

        for (Position position : game.getPositionList()) {
            if (engineMove != null) {
                count++;
                updateMoveList(moveList, count);
                //if (count > 20) {
                //    break;
                //}
                analysePosition(lastSFEN, engineMove, analysisTable, analysisTableModel, count);
            }
            lastSFEN = sfen;
            sfen = position.getGameSFEN();
            engineMove = position.getEngineMove();
        }

        quitEngine();
    }

    private void updateMoveList(JList moveList, final int index) {
        try {
            java.awt.EventQueue.invokeAndWait(()
                    -> moveList.setSelectedIndex(index));
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(GameAnalyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void initializeEngine(Engine engine) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(engine.getPath());
            processBuilder.directory((new File(engine.getPath())).getParentFile());
            process = processBuilder.start();
        } catch (IOException ex) {
            Logger.getLogger(EngineManager.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        stdin = process.getOutputStream();
        stdout = process.getInputStream();

        bufferedReader = new BufferedReader(new InputStreamReader(stdout));
    }

    private void initiateUSIProtocol() throws IOException {
        System.out.println("send: usi");
        stdin.write("usi\n".getBytes());
        stdin.flush();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            System.out.println(line);
            if (line.contains("usiok")) {
                return;
            }
        }
    }

    private void setOptions(Engine engine) throws IOException {
        for (EngineOption option : engine.getEngineOptionList()) {
            if (!option.getDef().contentEquals(option.getValue())) {
                System.out.println("send: setoption " + option.getName() + " value " + option.getValue());
                stdin.write(("setoption " + option.getName() + " value " + option.getValue() + "\n").getBytes());
            }
        }
        stdin.write(("setoption USI_AnalyseMode value true\n").getBytes());
    }

    private void getReady() throws IOException {
        System.out.println("send: isready");
        stdin.write("isready\n".getBytes());
        stdin.flush();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            System.out.println(line);
            if (line.contains("readyok")) {
                System.out.println("send: usinewgame");
                stdin.write("usinewgame\n".getBytes());
                stdin.flush();
                return;
            }
        }
    }

    private void quitEngine() throws IOException {
        System.out.println("send: quit");
        stdin.write("quit\n".getBytes());
        stdin.flush();
        process.destroy();
    }

    private void analysePosition(String sfen, String engineMove, JTable analysisTable, DefaultTableModel analysisTableModel, int moveNum) throws IOException {
        System.out.println("send: " + "position sfen " + sfen + " " + engineMove);
        stdin.write(("position sfen " + sfen + " " + engineMove + "\n").getBytes());
        System.out.println("send: go btime 0 wtime 0 byoyomi 3000");
        stdin.write("go btime 0 wtime 0 byoyomi 3000\n".getBytes());
        stdin.flush();
        String line;
        String lastLine = "";
        while ((line = bufferedReader.readLine()) != null) {
            System.out.println(line);
            if (line.contains("bestmove")) {
                updateTableModel(analysisTable, analysisTableModel, getTableInsert(lastLine, moveNum, engineMove));
                return;
            }
            lastLine = line;
        }
    }
    
    private Object[] getTableInsert(String line, int moveNum, String engineMove) {
        boolean lower = false;
        boolean upper = false;
        boolean mate = false;
        boolean cp = false;
        String score = "";
        String[] splitLine = line.split(" ");
        for (int i = 0; i < splitLine.length; i++) {
            if (splitLine[i].contentEquals("lowerbound")) {
                lower = true;
            }
            if (splitLine[i].contentEquals("upperbound")) {
                upper = true;
            }
            if (splitLine[i].contentEquals("cp")) {
                cp = true;
                if (moveNum % 2 != 0) {
                    score = splitLine[i+1]; 
                } else {
                    score = Integer.toString(Integer.parseInt(splitLine[i+1])*-1); 
                }
            }
            if (splitLine[i].contentEquals("mate")) {
                mate = true;
                int scoreVal = 0;
                if (moveNum % 2 != 0) {
                    scoreVal = Integer.parseInt(splitLine[i+1]); 
                } else {
                    scoreVal = Integer.parseInt(splitLine[i+1])*-1; 
                }
                if (scoreVal > 0) {
                    score = "+Mate:";
                } else {
                    score = "+Mate:";
                }
                score += Math.abs(scoreVal);
            }
        }
        
        
        boolean pv = false;
        String pvStr = "";
        for (int i = 0; i < splitLine.length; i++) {
            if (pv) {
                pvStr += splitLine[i] + " ";
            } else
            if (splitLine[i].contentEquals("pv")) {
                pv = true;
            }         
        }
        
        String lowUp = "";
        if (lower) {
            lowUp = "--";
        }
        
        if (upper) {
            lowUp = "++";
        }
        
        return new Object[]{moveNum + " " + engineMove, "", score, lowUp, pvStr};
    }

    private void updateTableModel(JTable analysisTable, DefaultTableModel analysisTableModel, Object[] newRow) {
        try {
            java.awt.EventQueue.invokeAndWait(()
                    -> {
                analysisTableModel.addRow(newRow);
                analysisTable.scrollRectToVisible(analysisTable.getCellRect(analysisTableModel.getRowCount()-1, 0, true));
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(GameAnalyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(GameAnalyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
