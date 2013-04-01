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


import java.awt.Point;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.ProductionAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template.TemplateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;

/**
 * This agent will first collect gold to produce a peasant,
 * then the two peasants will collect gold and wood separately until reach goal.
 * @author Derrick Tilsner
 * @author Sam Fleckenstein
 *
 */
public class ProbAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(ProbAgent.class.getCanonicalName());

	private int boardSizeRow;
	private int boardSizeColumn;
	private boolean hasSeen[][];
	private double towerProb[][];
	private int numVisits[][];
	private int numHits[][];
	private double pathFoundProb;
	private boolean seenGold = false;
	private Point goldLoc = new Point();
	private Integer goldId;
	
	private boolean producedPeasant;
	
	private PreviousState prevState;
	
//	private int goldRequired;//TODO are these needed?
	
	StateView currentState;
	private int step;
	private ArrayList<Integer> peasantIds;
	private ArrayList<Integer> townhallIds;
	
	private Direction directions[] = new Direction[8];

	public ProbAgent(int playernum, String[] arguments) {
		super(playernum);
		
		boardSizeRow = 0;
		boardSizeColumn = 0;
		
		pathFoundProb = 0.0;
		
		producedPeasant = false;
		
		directions[0] = Direction.NORTH;
		directions[1] = Direction.NORTHEAST;
		directions[2] = Direction.EAST;
		directions[3] = Direction.SOUTHEAST;
		directions[4] = Direction.SOUTH;
		directions[5] = Direction.SOUTHWEST;
		directions[6] = Direction.WEST;
		directions[7] = Direction.NORTHWEST;
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newState, History.HistoryView statehistory) {
		step = 0;
		currentState = newState;
		
		boardSizeColumn = currentState.getXExtent();
		boardSizeRow = currentState.getYExtent();
		
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
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		HashMap<Integer, Integer> peasantHP = new HashMap<Integer, Integer>();
		HashMap<Integer, Point> peasantLoc = new HashMap<Integer, Point>();
		peasantIds = new ArrayList<Integer>();
		townhallIds = new ArrayList<Integer>();
		for(int i = 0; i < allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			String unitTypeName = unit.getTemplateView().getName();			
			if(unitTypeName.equals("Townhall")) {
				townhallIds.add(id);
			}
			if(unitTypeName.equals("Peasant")) {
				peasantIds.add(id);
				peasantHP.put(id, unit.getHP());
				Point unitLoc = new Point();
				unitLoc.x = unit.getXPosition();
				unitLoc.y = unit.getYPosition();
				peasantLoc.put(id, unitLoc);
			}
		}
		
		prevState = new PreviousState(peasantIds, peasantHP, peasantLoc);
		
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
		
		//ANALYZE PHASE
		
		prevState.removeMarked();
		
		//TODO this assumes that the peasant takes one turn to produce
		if(producedPeasant) {
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
			int peasantID = peasantIds.get(peasantIds.size() - 1);
			UnitView peasant = currentState.getUnit(peasantID);
			prevState.addPeasant(peasantID, peasant.getHP(), new Point(peasant.getXPosition(), peasant.getYPosition()));
			producedPeasant = false;
		}
		
		for(int peasantID : prevState.getPeasantIds()) {
			//TODO fix this so that it actually checks if someone got killed
			if(!currentState.getUnitIds(0).contains(peasantID)) {
				Point peasantLoc = prevState.getPeasantLoc(peasantID);
				numHits[peasantLoc.x][peasantLoc.y]++;
				numVisits[peasantLoc.x][peasantLoc.y]++;
				
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
				prevState.markForRemoval(peasantID);
				continue;
			}
			
			UnitView peasant = currentState.getUnit(peasantID);
			
			int peasantX = peasant.getXPosition();
			int peasantY = peasant.getYPosition();
			setSeenLocations(peasantX, peasantY);
			numVisits[peasantX][peasantY]++;
			if(prevState.getPeasantHP(peasantID) > peasant.getHP()) { //got hit
				numHits[peasantX][peasantY]++;
				prevState.setPeasantHP(peasantID, peasant.getHP());
				//TODO update tower probabilities
			} else { //didn't get hit
				//TODO update tower probabilities
			}
			
			//DECIDE MOVE PHASE
			Action b = null;
			
			if(peasantIds.size() == 1 && currentState.getResourceAmount(0, ResourceType.GOLD) >= 400) {
				TemplateView peasantTemplate = currentState.getTemplate(0, "Peasant");
				int peasantTemplateId = peasantTemplate.getID();
				b = new ProductionAction(townhallIds.get(0), ActionType.COMPOUNDPRODUCE, peasantTemplateId);
				builder.put(peasantID, b);
				producedPeasant = true;
			}
			
			if(seenGold && peasant.getCargoAmount() == 0 && adjacentToGold(peasant)) { //adjacent to gold and has nothing in hand, gather
				b = new TargetedAction(peasantID, ActionType.COMPOUNDGATHER, goldId);
			} else if(peasant.getCargoAmount() != 0 && adjacentToTownhall(peasant)) { //adjacent to townhall and has something in hand, deposit
				b = new TargetedAction(peasantID, ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
			} else { //move somewhere
				Direction toMove = findNextMove(peasantID);
				b = new DirectedAction(peasantID, ActionType.PRIMITIVEMOVE, toMove);
			}
			
			builder.put(peasantID, b);
		}

		//EXECUTE MOVE PHASE
		
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

	/**
	 * 
	 * @param peasant - The peasant you are concerned with
	 * @return True if the peasant is next to the townhall
	 */
	private boolean adjacentToTownhall(UnitView peasant) {
		UnitView townhall = currentState.getUnit(townhallIds.get(0));
		Point townhallLoc = new Point();
		townhallLoc.x = townhall.getXPosition();
		townhallLoc.y = townhall.getYPosition();
		
		Point peasantLoc = new Point();
		peasantLoc.x = peasant.getXPosition();
		peasantLoc.y = peasant.getYPosition();
		
		if(Math.abs(townhallLoc.x - peasantLoc.x) <= 1
				&& Math.abs(townhallLoc.y - peasantLoc.y) <= 1) {
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @param peasant - The peasant you are concerned with
	 * @return True if the peasant is next to the gold
	 */
	private boolean adjacentToGold(UnitView peasant) {
		Point peasantLoc = new Point();
		peasantLoc.x = peasant.getXPosition();
		peasantLoc.y = peasant.getYPosition();
		if(Math.abs(peasantLoc.x - goldLoc.x) <= 1
				&& Math.abs(peasantLoc.y - goldLoc.y) <= 1) {
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param x - x peasant location
	 * @param y - y peasant location
	 */
	private void setSeenLocations(int x, int y) {
		//sets every location within range of sight to true
		int westColumn = x - 2;
		if(westColumn < 0) {
			westColumn = 0;
		}
		int eastColumn = x + 2;
		if(eastColumn > boardSizeColumn) {
			eastColumn = boardSizeColumn - 1;
		}
		int northRow = y - 2;
		if(northRow < 0) {
			northRow = 0;
		}
		int southRow = y + 2;
		if(southRow > boardSizeRow) {
			southRow = boardSizeRow - 1;
		}
		
		for(int i = northRow; i <= southRow; i++) {
			for(int j = westColumn; j <= eastColumn; j++) {
				hasSeen[i][j] = true;
				
				if(currentState.isResourceAt(i, j)) {
					Integer resource = currentState.resourceAt(i, j);
					if(currentState.getResourceNode(resource).getType().equals(ResourceType.GOLD)) {
						seenGold = true;
						goldLoc = new Point(i, j);
						goldId = resource;
					}
				} else if(currentState.unitAt(i, j) != null && currentState.unitAt(i, j) != townhallIds.get(0)) {
					towerProb[i][j] = 1;
				} else {
					towerProb[i][j] = 0;
				}
			}
		}
	}
	
	/**
	 * 
	 * @param peasantID - The ID of the peasant you are concerned with
	 * @return The direction with the least probability of getting hit
	 */
	private Direction findNextMove(int peasantID) {
		//TODO calculate probabilities of getting hit at each adjacent location
		//make sure nothing is gonna be there (like a tree or another peasant)
		
		//make it easy to add an objective function
//		double moveProbs[] = new double[8];
		
		//iterate over all 8 directions
		//find which ones take you closer to the gold or townhall (depending on what the peasant is carrying)
		//find which of those has the lowest probability of getting hit
		
		double minProb = 1;
		Direction dirToMove = null;
		
		UnitView peasant = currentState.getUnit(peasantID);
		int currentX = peasant.getXPosition();
		int currentY = peasant.getYPosition();
		
		int deltaX = 0;
		int deltaY = 0;
		for(Direction dir : directions) {
			switch(dir) {
			case NORTH:
				deltaX = 0;
				deltaY = -1;
				break;
			case NORTHEAST:
				deltaX = 1;
				deltaY = -1;
				break;
			case EAST:
				deltaX = 1;
				deltaY = 0;
				break;
			case SOUTHEAST:
				deltaX = 1;
				deltaY = 1;
				break;
			case SOUTH:
				deltaX = 0;
				deltaY = 1;
				break;
			case SOUTHWEST:
				deltaX = -1;
				deltaY = 1;
				break;
			case WEST:
				deltaX = -1;
				deltaY = 0;
				break;
			case NORTHWEST:
				deltaX = -1;
				deltaY = -1;
				break;
			}
			
			if(!currentState.inBounds(currentX + deltaX, currentY + deltaY)
					|| currentState.isResourceAt(currentX + deltaX, currentY + deltaY)) { //TODO what if another person will be there on the next turn?
				continue;
			}
			
			double currentProb = probOfGettingHit(currentX + deltaX, currentY + deltaY) 
					+ objectiveFunction(currentX + deltaX, currentY + deltaY);
			if(currentProb < minProb) {
				minProb = currentProb;
				dirToMove = dir;
			}
		}
		
		return dirToMove;
	}

	/**
	 * 
	 * @param x - x coordinate of peasant's location
	 * @param y - y coordinate of peasant's location
	 * @return Factors in utility of getting closer to goal
	 */
	private double objectiveFunction(int x, int y) {
		//should return negative numbers for useful places
		//0 <= Math.abs(return value) <= 1
		return 0;
	}


	/**
	 * 
	 * @param x - Potential x location
	 * @param y - Potential y location
	 * @return Probability of getting hit at (x, y)
	 */
	private double probOfGettingHit(int x, int y) {
		//TODO calculate probability of getting hit at (x,y)
		return 0.0;
	}
	
	/**
	 * Updates the probabilities of towers being at various locations
	 * given whether or not the peasant got hit at its current location.
	 * @param gotHit - True if the peasant got hit at the location
	 * @param peasantLoc - The peasants location
	 */
	private void updateTowerProbs(boolean gotHit, Point peasantLoc) {
		int x = peasantLoc.x;
		int y = peasantLoc.y;
		
		int westColumn = x - 5;
		if(westColumn < 0) {
			westColumn = 0;
		}
		int eastColumn = x + 5;
		if(eastColumn > boardSizeColumn) {
			eastColumn = boardSizeColumn - 1;
		}
		int northRow = y - 5;
		if(northRow < 0) {
			northRow = 0;
		}
		int southRow = y + 5;
		if(southRow > boardSizeRow) {
			southRow = boardSizeRow - 1;
		}
		
		for(int i = northRow; i <= southRow; i++) {
			for(int j = westColumn; j <= eastColumn; j++) {
				if(hasSeen[i][j]) {
					continue;
				}
				
			}
		}
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
