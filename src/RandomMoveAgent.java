/**
 *  Strategy Engine for Programming Intelligent Agents (SEPIA)
    Copyright (C) 2012 Case Western Reserve University

    This file is part of SEPIA.

    SEPIA is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SEPIA is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.
 */
//package edu.cwru.sepia.agent;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;

/**
 * This agent will first collect gold to produce a peasant,
 * then the two peasants will collect gold and wood separately until reach goal.
 * @author Derrick Tilsner
 * @author Sam Fleckenstein
 *
 */
public class RandomMoveAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(RandomMoveAgent.class.getCanonicalName());

	private int boardSizeRow;
	private int boardSizeColumn;
	private boolean hasSeen[][];
	private double towerProb[][];
	private int numVisits[][];
	private int numHits[][];
	private double pathFoundProb;
	
//	private int goldRequired;//TODO are these needed?
//	private int woodRequired;
	
	StateView currentState;
	private int step;
	private ArrayList<Integer> peasantIds;

	public RandomMoveAgent(int playernum, String[] arguments) {
		super(playernum);
		
		boardSizeRow = 0;
		boardSizeColumn = 0;
		
		pathFoundProb = 0.0;
		
		hasSeen = new boolean[boardSizeRow][boardSizeColumn];
		towerProb = new double[boardSizeRow][boardSizeColumn];
		numVisits = new int[boardSizeRow][boardSizeColumn];
		numHits = new int[boardSizeRow][boardSizeColumn];
		
		for(int i = 0; i < boardSizeRow; i++) {
			for(int j = 0; j < boardSizeColumn; j++) {
				hasSeen[i][j] = false;
				towerProb[i][j] = 0.0;
				numVisits[i][j] = 0;
				numHits[i][j] = 0;
			}
		}
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newState, History.HistoryView statehistory) {
		step = 0;
		currentState = newState;
		
		boardSizeColumn = currentState.getXExtent();
		boardSizeRow = currentState.getYExtent();
		
		return middleStep(newState, statehistory);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		currentState = newState;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}
		Map<Integer, Action> builder = new HashMap<Integer, Action>();
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		peasantIds = new ArrayList<Integer>();
		for(int i = 0; i < allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			String unitTypeName = unit.getTemplateView().getName();			
			if(unitTypeName.equals("Peasant")) {
				peasantIds.add(id);
			}
		}
		
		for(int peasant : peasantIds) {
			int peasantX = 0;//peasant.getXPosition();
			int peasantY = 0;//peasant.getYPosition();
			setSeenLocations(peasantX, peasantY);
			numVisits[peasantX][peasantY]++;
		}

		
		return builder;
	}

	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}

		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Congratulations! You have finished the task!");
		}
	}
	
	private void setSeenLocations(int x, int y) {
		//passes in location of the peasant
		//sets every location within range of sight to true
		int lowerRow = x - 2;
		if(lowerRow < 0) {
			lowerRow = 0;
		}
		int upperRow = x + 3;
		if(upperRow > boardSizeRow) {
			upperRow = boardSizeRow;
		}
		int lowerCol = y - 2;
		if(lowerCol < 0) {
			lowerCol = 0;
		}
		int upperCol = y + 3;
		if(upperCol > boardSizeColumn) {
			upperCol = boardSizeColumn;
		}
		
		for(int i = lowerRow; i < upperRow; i++) {
			for(int j = lowerCol; j < upperCol; j++) {
				hasSeen[i][j] = true;
			}
		}
	}
	
	private Direction findNextMove(int peasantID) {
		//TODO calculate probabilities of getting hit at each adjacent location
		//return the direction with the least probability of getting hit
		//make it easy to add an objective function
//		double moveProbs[] = new double[8];
		
		
		return Direction.NORTH;
	}
	
	private double probOfGettingHit(int x, int y) {
		//TODO calculate probability of getting hit at (x,y)
		return 0.0;
	}

	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
	}
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}