package com.chadfield.shogiexplorer.objectclasses;

import com.chadfield.shogiexplorer.main.EngineManager;
import com.chadfield.shogiexplorer.main.SFENParser;
import com.chadfield.shogiexplorer.objects.AnalysisParameter;
import com.chadfield.shogiexplorer.objects.Board;
import com.chadfield.shogiexplorer.objects.Coordinate;
import com.chadfield.shogiexplorer.objects.Engine;
import com.chadfield.shogiexplorer.objects.EngineOption;
import com.chadfield.shogiexplorer.objects.Game;
import com.chadfield.shogiexplorer.objects.Koma;
import com.chadfield.shogiexplorer.objects.Notation;
import com.chadfield.shogiexplorer.objects.Position;
import com.chadfield.shogiexplorer.utils.NotationUtils;
import com.chadfield.shogiexplorer.utils.ParserUtils;
import com.ibm.icu.text.Transliterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
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
    
    private Process process;
    private OutputStream stdin;
    private BufferedReader bufferedReader;
    private String lastScore = "";
    private String opinion = "";
    private int analysisTimePerMove;
    private int analysisMistakeThreshold;
    private int analysisBlunderThreshold;
    private int analysisIgnoreThreshold;

    public void analyse(Game game, Engine engine, JList<String> moveList, JTable analysisTable, AnalysisParameter analysisParam) throws IOException {
        this.analysisTimePerMove = analysisParam.getAnalysisTimePerMove();
        this.analysisMistakeThreshold = analysisParam.getAnalysisMistakeThreshold();
        this.analysisBlunderThreshold = analysisParam.getAnalysisBlunderThreshold();
        this.analysisIgnoreThreshold = analysisParam.getAnalysisIgnoreThreshold();
        initializeEngine(engine);
        initiateUSIProtocol();
        setOptions(engine);
        getReady();
        String engineMove = null;
        String japaneseMove = null;
        String sfen = null;
        String lastSFEN = null;
        int count = 0;
                
        for (Position position : game.getPositionList()) {
            if (engineMove != null) {
                count++;
                updateMoveList(moveList, count);
                analysePosition(game, lastSFEN, engineMove, japaneseMove, analysisTable, count);
            } 
            lastSFEN = sfen;
            sfen = position.getGameSFEN();
            engineMove = position.getNotation().getEngineMove();
            Transliterator trans = Transliterator.getInstance("Halfwidth-Fullwidth");

            if (count % 2 == 0) {
                japaneseMove = trans.transliterate(" ☗" + position.getNotation().getJapanese());
            } else {
                japaneseMove = trans.transliterate(" ☖" + position.getNotation().getJapanese());
            }
        }

        count++;
        updateMoveList(moveList, count);
        analysePosition(game, lastSFEN, engineMove, japaneseMove, analysisTable, count);
        
        quitEngine();
    }

    private void updateMoveList(JList<String> moveList, final int index) {
        try {
            java.awt.EventQueue.invokeAndWait(()
                    -> moveList.setSelectedIndex(index));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
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

        bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    private void initiateUSIProtocol() throws IOException {
        stdin.write("usi\n".getBytes());
        stdin.flush();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.contains("usiok")) {
                return;
            }
        }
    }

    private void setOptions(Engine engine) throws IOException {
        for (EngineOption option : engine.getEngineOptionList()) {
            if (!option.getDef().contentEquals(option.getValue())) {
                stdin.write(("setoption " + option.getName() + " value " + option.getValue() + "\n").getBytes());
            }
        }
        stdin.write(("setoption USI_AnalyseMode value true\n").getBytes());
    }

    private void getReady() throws IOException {
        stdin.write("isready\n".getBytes());
        stdin.flush();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.contains("readyok")) {
                stdin.write("usinewgame\n".getBytes());
                stdin.flush();
                return;
            }
        }
    }

    private void quitEngine() throws IOException {
        stdin.write("quit\n".getBytes());
        stdin.flush();
        process.destroy();
    }

    private void analysePosition(Game game, String sfen, String engineMove, String japaneseMove, JTable analysisTable, int moveNum) throws IOException {
        stdin.write(("position sfen " + sfen + " " + engineMove + "\n").getBytes());
        stdin.write(("go btime 0 wtime 0 byoyomi " + analysisTimePerMove*1000 + "\n").getBytes());
        stdin.flush();
        String line;
        List<String> lineList = new ArrayList<>();
        while ((line = bufferedReader.readLine()) != null) {
            if (line.contains("bestmove")) {
                String bestLine = getBestLine(line, lineList);
                ArrayList<Position> pvPositionList = getPVPositionList(sfen, bestLine);
                updateTableModel(analysisTable, getTableInsert(bestLine, moveNum, japaneseMove, pvPositionList));
                game.getAnalysisPositionList().add(pvPositionList);
                return;
            }
            lineList.add(line);
        }
    }
    
    private ArrayList<Position> getPVPositionList(String sfen, String bestLine) {
        ArrayList<Position> result = new ArrayList<>();
        String currentSfen = sfen;
        Coordinate lastDestination = null;
        for (String move : getBestLineMoveList(bestLine)) {
            Position position = getPosition(currentSfen, move, lastDestination);
            result.add(position);
            currentSfen  = position.getGameSFEN();
            lastDestination = position.getDestination();
        }
        return result;
    }
    
    private Position getPosition(String sfen, String move, Coordinate lastDestination) {
        Board board  = SFENParser.parse(sfen);
        Notation notation = executeMove(board, move, lastDestination);
        return new Position(SFENParser.getSFEN(board), board.getSource(), board.getDestination(), notation);
    } 
    
    private Notation executeMove(Board board, String move, Coordinate lastDestination) {
        Coordinate thisDestination;
        Coordinate thisSource;
        Koma.Type sourceKomaType;
        boolean isDrop = isDrop(move);
        String disambiguation;
        
        thisDestination = getDestinationCoordinate(move);
        if (thisDestination == null) {
            Notation notation = new Notation();
            notation.setJapanese(move);
            return notation;
        }
        if (isDrop) {
            thisSource = null;
            Koma sourceKoma = ParserUtils.getDropKoma(move.substring(0, 1), board.getNextTurn());
            sourceKomaType = sourceKoma.getType();
            disambiguation = NotationUtils.getDropNotation(board, thisDestination, sourceKomaType);
            executeDropMove(board, thisDestination, sourceKoma);
            
        } else {
            thisSource = getSourceCoordinate(move);
            Koma sourceKoma = getKoma(board, thisSource);
            sourceKomaType = sourceKoma.getType();
            disambiguation = NotationUtils.getDisambiguation(board, thisSource, thisDestination, sourceKomaType);
            executeRegularMove(board, thisSource, thisDestination, sourceKoma, move);
        }
        
        board.setNextTurn(ParserUtils.switchTurn(board.getNextTurn()));
        board.setSource(thisSource);
        board.setDestination(thisDestination);
        board.setMoveCount(board.getMoveCount()+1);
        
        Notation notation = new Notation();
        notation.setJapanese(getNotation(board, lastDestination, sourceKomaType, isDrop, move, disambiguation));

        return notation;
    }
    
    private static String getNotation(Board board, Coordinate lastDestination, Koma.Type sourceKomaType, boolean isDrop, String move, String disambiguation) {
        String result;
        if (board.getNextTurn() == Board.Turn.GOTE) {
            result = "☗";
        } else {
            result = "☖";
        }  
        
        Coordinate destination = board.getDestination();
        if (lastDestination != null && destination.sameValue(lastDestination)) {
            result += NotationUtils.SAME;
        } else {
            result +=  NotationUtils.getJapaneseCoordinate(destination);
        }
        
        result += NotationUtils.getKomaKanji(sourceKomaType);
        result += disambiguation;
        if (!isDrop && isPromoted(move)) {
            result += NotationUtils.PROMOTED;
        }
        return result;
    }
    
    private static void removePieceInHand(Koma.Type komaType, Board board) {
        if (board.getInHandKomaMap().get(komaType) == 1) {
            board.getInHandKomaMap().remove(komaType);
        } else {
            board.getInHandKomaMap().put(komaType, board.getInHandKomaMap().get(komaType) - 1);
        }
    }
        
    private static void executeDropMove(Board board, Coordinate thisDestination, Koma sourceKoma) {
        try {
            putKoma(board, thisDestination, sourceKoma);
            removePieceInHand(sourceKoma.getType(), board);
        } catch (Exception ex) {
            Logger.getLogger(GameAnalyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    private void executeRegularMove(Board board, Coordinate thisSource, Coordinate thisDestination, Koma sourceKoma, String move) {
        if (getKoma(board, thisDestination) != null) {
            Koma thisKoma = getKoma(board, thisDestination);
            if (thisKoma != null) {
                ParserUtils.addPieceToInHand(getKoma(board, thisDestination), board);
            }
        }
        putKoma(board, thisDestination, promCheck(sourceKoma, move));
        putKoma(board, thisSource, null); 
    }
    
    private static Koma promCheck(Koma koma, String move) {
        if (!isPromoted(move)) {
            return koma;
        } else {
            return ParserUtils.promoteKoma(koma.getType());
        }
    }
    
    private static boolean isPromoted(String move) {
        return move.charAt(move.length()-1) == '+';
    }
    
    public static void putKoma(Board board, Coordinate coords, Koma koma) {
            board.getMasu()[9 - coords.getX()][coords.getY() - 1] = koma;
    }
    
    public static Koma getKoma(Board board, Coordinate coords) {
        return board.getMasu()[9 - coords.getX()][coords.getY() - 1];
    }
    
    private boolean isDrop(String move) {
        return move.contains("*");
    }
    
    private Coordinate getDestinationCoordinate(String move) {
        Coordinate result = new Coordinate();
        if (move.contains("*")) {
            int starIndex = move.indexOf("*");
            result.setX(move.charAt(starIndex+1)-49+1);
            result.setY(move.charAt(starIndex+2)-97+1);   
        }  else {
            result.setX(move.charAt(2)-49+1);
            result.setY(move.charAt(3)-97+1);   
        }
        if (result.getX() > 9 || result.getX() < 1 || result.getY() > 9 || result.getY() < 1) {
            return null;
        }
        return result;
    }
    
    private Coordinate getSourceCoordinate(String move) {
        Coordinate result = new Coordinate();
        result.setX(move.charAt(0)-49+1);
        result.setY(move.charAt(1)-97+1);  
        if (result.getX() > 9 || result.getX() < 1 || result.getY() > 9 || result.getY() < 1) {
            return null;
        }

        return result;
    }
    
    private String getBestLine(String line, List<String> lineList) {
        String bestMove = line.split(" ")[1];
        int lineListSize = lineList.size();
        for (int j = lineListSize - 1; j >= 0; j--) {
            if (lineList.get(j).contains("pv " + bestMove)) {
                return lineList.get(j);
            }
        }
        return lineList.get(lineListSize - 1);
    }

    private Object[] getTableInsert(String lastLine, int moveNum, String japaneseMove, ArrayList<Position> pvPositionList) {
        boolean lower = false;
        boolean upper = false;
        boolean foundPV = false;
        String score = "";
        String[] splitLine = lastLine.split(" ");
        for (int i = 0; i < splitLine.length; i++) {
            if (!foundPV) {
                switch (splitLine[i]) {
                    case "lowerbound":
                        lower = true;
                        break;
                    case "upperbound":
                        upper = true;
                        break;
                    case "cp":
                        score = getScore(moveNum, splitLine[i+1]);
                        opinion = compareScore(moveNum, lastScore,  score);
                        lastScore = String.copyValueOf(score.toCharArray());
                        break;
                    case "mate":
                        score = getMateScore(moveNum, splitLine[i+1]);
                        break;
                    case "pv":
                        foundPV = true;
                        break;
                    default:
                }       
            }
        }
        
        StringBuilder pvBuilder = new StringBuilder("");
        
        for (Position position: pvPositionList) {
            pvBuilder.append(position.getNotation().getJapanese());
            pvBuilder.append(" ");
        }
        

        Transliterator trans = Transliterator.getInstance("Halfwidth-Fullwidth");
        String pvStr = trans.transliterate(pvBuilder.toString().trim());
        
        String lowUp = getLowUpString(lower, upper);

        return new Object[]{moveNum + japaneseMove, "", score, lowUp, pvStr};
    }
        
    private List<String> getBestLineMoveList(String line) {
        List<String> result = new ArrayList<>();
        boolean foundPV = false;
        String[] splitLine = line.split(" ");
        for (int i = 0; i < splitLine.length; i++) {
            if (!foundPV && splitLine[i].contentEquals("pv")) {
                for (int j = i+1; j < splitLine.length; j++ ) {
                    if (!splitLine[j].contains("%")) {
                        result.add(splitLine[j]);
                    }
                }
                foundPV = true;
            }
        }
        return result;
    }
    
    private String compareScore(int moveNum, String lastScore, String score) {
        int lastScoreVal;
        int scoreVal;
        
        if (lastScore.isEmpty() || score.isEmpty()) {
            return "";
        }
                
        lastScoreVal = getLastScoreVal();
                
        scoreVal = getScoreVal(score);
                
        if ((scoreVal >= analysisIgnoreThreshold && lastScoreVal >= analysisIgnoreThreshold) || (scoreVal <= -analysisIgnoreThreshold && lastScoreVal <= -analysisIgnoreThreshold)) {
            return "";
        }
        
        return assessScores(moveNum, lastScoreVal, scoreVal);
    }
    
    private String assessScores(int moveNum, int lastScoreVal, int scoreVal) {
        // We are finding the opinion for the PREVIOUS move.
        if (moveNum % 2 != 0) {
            // This move is for sente.
            if (scoreVal - lastScoreVal > analysisBlunderThreshold) {
                return "??";
            }
            if (scoreVal - lastScoreVal > analysisMistakeThreshold) {
                return "?";
            }
        } else {
            // This move is for gote.
            if (scoreVal - lastScoreVal < -analysisBlunderThreshold) {
                return "??";
            }
            if (scoreVal - lastScoreVal < -analysisMistakeThreshold) {
                return "?";
            }
        }
        return "";
    }
    
    private int getScoreVal(String score) {
        if (score.contains("+Mate")) {
            return -31111;
        } else if (score.contains("-Mate")) {
            return 31111;
        } else {
            return Integer.parseInt(score);
        }
    }
    
    private int getLastScoreVal() {
        if (lastScore.contains("+Mate")) {
            return 31111;
        } else if (lastScore.contains("-Mate")) {
            return -31111;
        } else {
            return Integer.parseInt(lastScore);
        }
    }
    
    private String getMateScore(int moveNum, String value) {
        int scoreVal;
        String score;
        if (moveNum % 2 != 0) {
            scoreVal = Integer.parseInt(value);
        } else {
            scoreVal = Integer.parseInt(value) * -1;
        }
        if (scoreVal > 0) {
            score = "+Mate:";
        } else {
            score = "-Mate:";
        }
        score += Math.abs(scoreVal);
        return score;
    }

    private String getScore(int moveNum, String value) {
        if (moveNum % 2 != 0) {
            return value;
        } else {
            return Integer.toString(Integer.parseInt(value) * -1);
        }
    }

    private String getLowUpString(boolean lower, boolean upper) {
        if (lower) {
            return "--";
        }
        if (upper) {
            return "++";
        }
        return "";
    }

    private void updateTableModel(JTable analysisTable, Object[] newRow) {
        DefaultTableModel analysisTableModel = (DefaultTableModel) analysisTable.getModel();
        try {
            java.awt.EventQueue.invokeAndWait(()
                    -> {
                if (!opinion.isEmpty()) {
                    analysisTableModel.setValueAt(opinion, analysisTableModel.getRowCount()-1, 1);
                    opinion = "";
                }
                analysisTableModel.addRow(newRow);
                analysisTable.scrollRectToVisible(analysisTable.getCellRect(analysisTableModel.getRowCount() - 1, 0, true));
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            Logger.getLogger(GameAnalyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
