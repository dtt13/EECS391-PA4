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
 * @author Derrick Tilsner
 * @author Sam Fleckenstein
 *
 */
public class ProbAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(ProbAgent.class.getCanonicalName());
	private static final double APPROX_TOWER_DENSITY = 0.0085;
//	private static final Point[] deltaValues = new Point[16];
	private static final double MAX_EXPLORE_FACTOR = .2;

	private int boardSizeRow;
	private int boardSizeColumn;
	private boolean hasSeen[][];
	private double towerProb[][];
	private int goodPath[][];
	private int numVisits[][];
	private int numHits[][];
	private boolean seenGold = false;
	private Point goldLoc = new Point();
	private Integer goldId;
	
	private boolean producedPeasant;
	
	private PreviousState prevState;
	
	StateView currentState;
	private int step;
	private ArrayList<Integer> peasantIds;
	private ArrayList<Integer> townhallIds;
	private ArrayList<Point> nextPeasantLocs;
	
	private Direction directions[] = new Direction[8];
	private double avgBoardSize;
	private double exploreCoeff;

	public ProbAgent(int playernum, String[] arguments) {
		super(playernum);
		
		boardSizeRow = 0;
		boardSizeColumn = 0;
		
//		pathFoundProb = 0.0;
		
		producedPeasant = false;
		
		directions[0] = Direction.NORTHEAST;
		directions[1] = Direction.SOUTHEAST;
		directions[2] = Direction.SOUTHWEST;
		directions[3] = Direction.NORTHWEST;
		directions[4] = Direction.NORTH;
		directions[5] = Direction.EAST;
		directions[6] = Direction.SOUTH;
		directions[7] = Direction.WEST;
		
//		deltaValues[0] = new Point(-4, 0);
//		deltaValues[1] = new Point(-3, -1);
//		deltaValues[2] = new Point(-3, 0);
//		deltaValues[3] = new Point(-3, 1);
//		
//		deltaValues[4] = new Point(4, 0);
//		deltaValues[5] = new Point(3, -1);
//		deltaValues[6] = new Point(3, 0);
//		deltaValues[7] = new Point(3, 1);
//		
//		deltaValues[8] = new Point(0, -4);
//		deltaValues[9] = new Point(1, -3);
//		deltaValues[10] = new Point(0, -3);
//		deltaValues[11] = new Point(-1, -3);
//		
//		deltaValues[12] = new Point(0, 4);
//		deltaValues[13] = new Point(1, 3);
//		deltaValues[14] = new Point(0, 3);
//		deltaValues[15] = new Point(-1, 3);
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newState, History.HistoryView statehistory) {
		step = 0;
		currentState = newState;
		
		boardSizeColumn = currentState.getXExtent();
		boardSizeRow = currentState.getYExtent();
		
		avgBoardSize = (boardSizeColumn + boardSizeRow);
		exploreCoeff = MAX_EXPLORE_FACTOR * (2 / avgBoardSize) * (2 / avgBoardSize);
		
		goldLoc.x = boardSizeColumn - 5;
		goldLoc.y = 2;
		
		hasSeen = new boolean[boardSizeColumn][boardSizeRow];
		towerProb = new double[boardSizeColumn][boardSizeRow];
		goodPath = new int[boardSizeColumn][boardSizeRow];
		numVisits = new int[boardSizeColumn][boardSizeRow];
		numHits = new int[boardSizeColumn][boardSizeRow];
		
		for(int i = 0; i < boardSizeColumn; i++) {
			for(int j = 0; j < boardSizeRow; j++) {
				hasSeen[i][j] = false;
				towerProb[i][j] = APPROX_TOWER_DENSITY;
//				towerProb[i][j] = 0;
				goodPath[i][j] = 0;
				numVisits[i][j] = 0;
				numHits[i][j] = 0;
			}
		}
//		boolean smallMap = (boardSizeColumn < 32);
//		if(smallMap) {
//			towerProb[2][1] = 1;
//			towerProb[14][7] = 1;//TODO remember to not hard code this
//			towerProb[6][10] = 1;
//			towerProb[16][17]= 1;
//		} else {
//			towerProb[5][3] = 1;
//			towerProb[14][7] = 1;
//			towerProb[16][8] = 1;
//			towerProb[22][11] = 1;
//			towerProb[24][11] = 1;
//			towerProb[6][13] = 1;
//			towerProb[8][13] = 1;
//			towerProb[30][17] = 1;
//			towerProb[24][21] = 1;
//			towerProb[23][25] = 1;
//			towerProb[6][24] = 1;
//		}
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		HashMap<Integer, Integer> peasantHP = new HashMap<Integer, Integer>();
		HashMap<Integer, Point> peasantLoc = new HashMap<Integer, Point>();
		peasantIds = new ArrayList<Integer>();
		townhallIds = new ArrayList<Integer>();
		for(int i = 0; i < allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			String unitTypeName = unit.getTemplateView().getName();			
			if(unitTypeName.equals("TownHall")) {
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
		
		prevState = new PreviousState(peasantIds, peasantHP, peasantLoc, boardSizeColumn, boardSizeRow);
		
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
		
		nextPeasantLocs = new ArrayList<Point>();
		
		for(int peasantID : prevState.getPeasantIds()) {
			if(!currentState.getUnitIds(0).contains(peasantID)) {		//peasant dies
				Point peasantLoc = prevState.getPeasantLoc(peasantID);
				numHits[peasantLoc.x][peasantLoc.y]++;
				numVisits[peasantLoc.x][peasantLoc.y]++;
				updateTowerProbs(true, peasantLoc.x, peasantLoc.y);
				
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
		}
			
		int peasantID = peasantIds.get(0);
			UnitView peasant = currentState.getUnit(peasantID);
			int peasantX = peasant.getXPosition();
			int peasantY = peasant.getYPosition();
			setSeenLocations(peasantID, peasantX, peasantY);
			numVisits[peasantX][peasantY]++;
			if(prevState.getPeasantHP(peasantID) > peasant.getHP()) { //got hit
				numHits[peasantX][peasantY]++;
				prevState.setPeasantHP(peasantID, peasant.getHP());
				updateTowerProbs(true, peasantX, peasantY);
			} else { //didn't get hit
				updateTowerProbs(false, peasantX, peasantY);
			}
			
			//DECIDE MOVE PHASE
			Action b = null;
			
			//townhall actions
			if(peasantIds.size() == 1 && currentState.getResourceAmount(0, ResourceType.GOLD) >= 400) {
				TemplateView peasantTemplate = currentState.getTemplate(0, "Peasant");
				int peasantTemplateId = peasantTemplate.getID();
				b = new ProductionAction(townhallIds.get(0), ActionType.COMPOUNDPRODUCE, peasantTemplateId);
				builder.put(townhallIds.get(0), b);
				producedPeasant = true;
			}
			
			//peasant actions
			if(seenGold && peasant.getCargoAmount() == 0 && adjacentToGold(peasant)) { //adjacent to gold and has nothing in hand, gather
				nextPeasantLocs.add(new Point(peasantX, peasantY));
				b = new TargetedAction(peasantID, ActionType.COMPOUNDGATHER, goldId);
			} else if(peasant.getCargoAmount() != 0 && adjacentToTownhall(peasant)) { //adjacent to townhall and has something in hand, deposit
				nextPeasantLocs.add(new Point(peasantX, peasantY));
				b = new TargetedAction(peasantID, ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
			} else { //move somewhere
				Direction toMove = findNextMove(peasantID);
				b = new DirectedAction(peasantID, ActionType.PRIMITIVEMOVE, toMove);
			}
			
			builder.put(peasantID, b);
//		}
		
		printTowerProbs();
		printGoodPath();

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
	private void setSeenLocations(int peasantId, int x, int y) {
		//determine if you're on a good path
		UnitView peasant = currentState.getUnit(peasantId);
		boolean hasCargo = (peasant.getCargoAmount() > 0);
		Boolean hadCargo = prevState.getHasCargo(peasantId, x, y);
		if(hadCargo != null && hadCargo.booleanValue() == hadCargo) {
			goodPath[x][y] -= 2;
		} else {
			goodPath[x][y] += 1;
		}
//		if(goodPath[x][y] > 6) {
//			goodPath[x][y] = 6;
//		} else if(goodPath[x][y] < -6) {
//			goodPath[x][y] = -6;
//		}
		prevState.setHasCargo(peasantId, x, y, hasCargo);
		//sets every location within range of sight to true
		Point seen = new Point();
		for(int i = -2; i <= 2; i++) {
			for(int j = -2; j <= 2; j++) {
				seen.x = x + i;
				seen.y = y + j;
				
				if(currentState.inBounds(seen.x, seen.y)) {
					hasSeen[seen.x][seen.y] = true;
					Integer unitID = currentState.unitAt(seen.x, seen.y);
					
					if(currentState.isResourceAt(seen.x, seen.y)) {
						Integer resource = currentState.resourceAt(seen.x, seen.y);
						if(currentState.getResourceNode(resource).getType().toString().equals("GOLD_MINE")) {
							seenGold = true;
							goldLoc = new Point(seen.x, seen.y);
							goldId = resource;
						}
						towerProb[seen.x][seen.y] = 0.0;
					} else if(unitID != null 
							&& unitID != townhallIds.get(0)
							&& !peasantIds.contains(unitID)) {
						towerProb[seen.x][seen.y] = 1.0;
					} else {
						towerProb[seen.x][seen.y] = 0.0;
					}
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
		double minProb = 9999999;
		Direction dirToMove = null;
		
		UnitView peasant = currentState.getUnit(peasantID);
		int currentX = peasant.getXPosition();
		int currentY = peasant.getYPosition();
		
		int newX = currentX;
		int newY = currentY;
		
		UnitView townhall = currentState.getUnit(townhallIds.get(0));
		int townhallX = townhall.getXPosition();
		int townhallY = townhall.getYPosition();
		
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
			
			Point nextLoc = new Point(currentX + deltaX, currentY + deltaY);
			
			boolean moveOntoTownhall = ((currentX + deltaX == townhallX) && (currentY + deltaY == townhallY));
			boolean hasCargo = peasant.getCargoAmount() > 0;
			if(!currentState.inBounds(currentX + deltaX, currentY + deltaY)
					|| currentState.isResourceAt(currentX + deltaX, currentY + deltaY)
					|| moveOntoTownhall
					|| nextPeasantLocs.contains(nextLoc)) {
				continue;
			}
			
			double currentProb = probOfGettingHit(currentX + deltaX, currentY + deltaY) 
					+ objectiveFunction(!hasCargo, currentX, currentY, currentX + deltaX, currentY + deltaY);
			if(currentProb < minProb) {
				minProb = currentProb;
				dirToMove = dir;
				newX = currentX + deltaX;
 				newY = currentY + deltaY;
			}
		}
//		System.out.println("PeasantId: " + peasantID + " min prob: " + minProb + " (x, y) " + newX + " " + newY);
		nextPeasantLocs.add(new Point(newX, newY));
		return dirToMove;
	}

	/**
	 * 
	 * @param toGold - True if the peasant is moving towards the gold
	 * @param currentX - The peasant's current x coordinate
	 * @param currentY - The peasant's current y coordinate
	 * @param nextX - The potential next x coordinate
	 * @param nextY - The potential next y coordinate
	 * @return Factors in exploration, rather than just safety of next moves
	 */
	private double objectiveFunction(boolean toGold, int currentX, int currentY, int nextX, int nextY) {
		int peasantId = currentState.unitAt(currentX, currentY);
		UnitView peasant = currentState.getUnit(peasantId);
		
		double objectiveValue = 0;
		int currentDistance = 0;
		int nextDistance = 0;
		if(toGold) {
			currentDistance += Math.abs(goldLoc.x - currentX) + Math.abs(goldLoc.y - currentY);
			nextDistance += Math.abs(goldLoc.x - nextX) + Math.abs(goldLoc.y - nextY);
//			currentDistance = Math.max(Math.abs(goldLoc.x - currentX), Math.abs(goldLoc.y - currentY));
//			nextDistance = Math.max(Math.abs(goldLoc.x - nextX), Math.abs(goldLoc.y - nextY));
		} else {
			UnitView townhall = currentState.getUnit(townhallIds.get(0));
			currentDistance += Math.abs(townhall.getXPosition() - currentX) + Math.abs(townhall.getYPosition() - currentY);
			nextDistance += Math.abs(townhall.getXPosition() - nextX) +  Math.abs(townhall.getYPosition() - nextY);
//			currentDistance = Math.max(Math.abs(townhall.getXPosition() - currentX), Math.abs(townhall.getYPosition() - currentY));
//			nextDistance = Math.max(Math.abs(townhall.getXPosition() - nextX), Math.abs(townhall.getYPosition() - nextY));
		}
		
		if(nextDistance < currentDistance) {
			objectiveValue -= 0.3 * (currentDistance - nextDistance);
		}
		
//		boolean hasCargo = (peasant.getCargoAmount() > 0);
//		Boolean hadCargo = prevState.getHasCargo(peasantId, nextX, nextY);
//		double exploreFactor = -exploreCoeff * currentDistance * (currentDistance - avgBoardSize);// * (-.5 * numVisits[nextX][nextY] + 1);
//		
//		if(hadCargo != null && hadCargo.booleanValue() != hasCargo) {
//			objectiveValue -= 0.2;
//		} else if(hadCargo == null) {
////		objectiveValue -= exploreFactor;
//			objectiveValue -= (numVisits[currentX][currentY] - numVisits[nextX][nextY]) * exploreFactor;
//		} else {
//			objectiveValue += 0.5;
//		}
		
//			System.out.println("Explore: " + exploreFactor);
//			System.out.println("Dist: " + (double)(currentDistance - avgBoardSize));
//			System.out.println("Linear: " + (double)(-.5 * numVisits[nextX][nextY] + 1));
//			objectiveValue -= 0.08 * goodPath[nextX][nextY];

		return objectiveValue;
	}
	
	/**
	 * Sums the probabilities there being a tower at
	 * all of the locations that are in range of the
	 * peasantLoc.
	 * @param peasantLoc - Potential peasant location
	 * @return Value proportional to probability of getting hit at (x, y)
	 */
	private double probOfGettingHit(int x, int y) {
		double totalProb = 0;
		
		//TODO this should be fixed to calculate the real value
		//for now, it makes the peasants slightly less retarded
//		if(numHits[x][y] > 0) {
//			return .75;
//		}
		
		Point tower = new Point();
		for(int i = -4; i <= 4; i++) {
			for(int j = -4; j <= 4; j++) {
				tower.x = x + j;
				tower.y = y + i;
				
				if(currentState.inBounds(tower.x, tower.y)) {
					totalProb += towerProb[tower.x][tower.y];
				}
				
				if(totalProb > .1) {
					return .75;
				}
			}
		}
		return 0;
		//TODO find the real deltaValues
//		for(Point p : deltaValues) {
//			tower.x = x + p.x;
//			tower.y = y + p.y;
//			
//			if(currentState.inBounds(tower.x, tower.y)) {
//				totalProb += towerProb[tower.x][tower.y];
//			}
//		}
		
//		for(int i = -2; i <= 2; i++) {
//			for(int j = -2; j <= 2; j++) {
//				tower.x = x + j;
//				tower.y = y + i;
//				
//				if(currentState.inBounds(tower.x, tower.y)) {
//					totalProb += towerProb[tower.x][tower.y];
//				}
//			}
//		}
		
		
//		if(x == 3 && y == 14) {
//			System.out.println(totalProb);
//		}
		
//		return totalProb;
	}
	
	/**
	 * Updates the probabilities of towers being at various locations
	 * given whether or not the peasant got hit at its current location.
	 * @param gotHit - True if the peasant got hit at the location
	 * @param x - The peasant's x location
	 * @param y - The peasant's y location
	 */
	private void updateTowerProbs(boolean gotHit, int x, int y) {
		double changedSum = 0;
		
		int numTowers = (int)(APPROX_TOWER_DENSITY * boardSizeColumn * boardSizeRow + 3);
		
		Point tower = new Point();
		if(gotHit) {
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]
							&& towerProb[tower.x][tower.y] != 1) {
						towerProb[tower.x][tower.y] *= binomialCoeff(numVisits[x][y], numHits[x][y])
								* Math.pow(0.75, numHits[x][y]) 
								* Math.pow(0.25, numVisits[x][y] - numHits[x][y]);
						changedSum += towerProb[tower.x][tower.y];
					}
				}
			}
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]
							&& towerProb[tower.x][tower.y] != 1) {
						towerProb[tower.x][tower.y] /= changedSum;
					}
				}
			}
		} else {
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]
							&& towerProb[tower.x][tower.y] != 1) {
						towerProb[tower.x][tower.y] *= (1 - binomialCoeff(numVisits[x][y], numHits[x][y])
								* Math.pow(0.75, numHits[x][y]) 
								* Math.pow(0.25, numVisits[x][y] - numHits[x][y]));
					}
				}
			}

			double totalSum = 0;
			for(int i = 0; i < towerProb.length; i++) {
				for(int j = 0; j < towerProb[i].length; j++) {
					if(currentState.inBounds(i, j) 
							&& !hasSeen[i][j]
							&& towerProb[i][j] != 1) {
						totalSum += towerProb[i][j];
					}
				}
			}
			
//			for(int i = 0; i < towerProb.length; i++) {
//				for(int j = 0; j < towerProb[i].length; j++) {
//					if(currentState.inBounds(i, j) 
//							&& !hasSeen[i][j]
//							&& towerProb[i][j] != 1) {
//						towerProb[i][j] = towerProb[i][j] / (totalSum * APPROX_TOWER_DENSITY);
//					}
//				}
//			}
			
			for(int i = -4; i <= 4; i++) {
				for(int j = -4; j <= 4; j++) {
					tower.x = x + j;
					tower.y = y + i;
					
					if(currentState.inBounds(tower.x, tower.y) 
							&& !hasSeen[tower.x][tower.y]
							&& towerProb[tower.x][tower.y] != 1) {
						towerProb[tower.x][tower.y] /= totalSum;
					}
				}
			}
		}
		
		
//		for(double[] array : towerProb) {
//			for(double prob : array) {
//				probSum += prob;
//			}
//		}
//		
//		for(int i = 0; i < towerProb.length; i++) {
//			for(int j = 0; j < towerProb[i].length; j++) {
//				if(towerProb[i][j] != 1) {
//					towerProb[i][j] /= probSum;
//				}
//			}
//		}
	}
	
	private double binomialCoeff(int numVisits, int numHits) {
		double numerator = 1;
		for(int i = numVisits; i > numHits; i--) {
			numerator *= i;
		}
		double denom = 1;
		for(int i = numVisits - numHits; i > 0; i--) {
			denom *= i;
		}
		
		return numerator / denom;
	}


	public void printTowerProbs() {
		for(int i = 0; i < towerProb.length; i++) {
			for(int j = 0; j < towerProb[i].length; j++) {
				System.out.print("" + i + ", " + j + " " + Math.floor(towerProb[i][j] * 10000) / 10000 + "  ");
			}
			System.out.println();
		}
		System.out.println();
		System.out.println();
		System.out.println();
	}

	public void printGoodPath() {
//		for(int i = 0; i < goodPath.length; i++) {
//			for(int j = 0; j < goodPath[i].length; j++) {
//				System.out.print("" + i + ", " + j + " " + goodPath[i][j] + "  ");
//			}
//			System.out.println();
//		}
//		System.out.println();
//		System.out.println();
//		System.out.println();
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
