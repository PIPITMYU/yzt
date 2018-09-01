package com.yzt.logic.util.GameUtil;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;
import com.yzt.logic.mj.domain.Action;
import com.yzt.logic.mj.domain.Player;
import com.yzt.logic.mj.domain.RoomResp;
import com.yzt.logic.util.BackFileUtil;
import com.yzt.logic.util.Cnst;
import com.yzt.logic.util.MahjongUtils;
import com.yzt.logic.util.RoomUtil;
import com.yzt.logic.util.redis.RedisUtil;

/**
 * 玩家分的统计
 * 
 * @author wsw_007
 *
 */
public class JieSuan {
	public static void xiaoJieSuan(String roomId) {
		RoomResp room = RedisUtil.getRoomRespByRoomId(roomId);
		List<Player> players = RedisUtil.getPlayerList(room);
		//需要做以下统计
		//以及大结算校验  这里会写小结算文件 并对房间进行初始化 
		boolean ziMo = false;//赢家是否自摸
		for (Player other : players) {
			if(other.getIsZiMo()){
				ziMo = true;
			}
		}
		//杠分单算,先取到每隔玩家的杠分.
		if (room.getScoreType() != 1
				|| (room.getScoreType() == 1 && ziMo)
				|| (room.getHuangZhuang() != null && room.getHuangZhuang() == true)) {
			for (Player player : players) {
				List<Action> actionList = player.getActionList();
				if (actionList != null && actionList.size() != 0) {
					for (Action action : actionList) {
						if (action.getType() == Cnst.ACTION_TYPE_DIANGANG || action.getType() == Cnst.ACTION_TYPE_PENGGANG) { // 明杠1分
							changeGangFen(players, player, 1);
						} else if (action.getType() == Cnst.ACTION_TYPE_ANGANG) { // 暗杠2分
							changeGangFen(players, player, 2);
						}
					}
				}
				List<Integer> huaList = MahjongUtils.getNewList(player
						.getHuaList());
				if (huaList.size() >= 3) {
					if (Cnst.PAI_HUA.contains(room.getDingHunPai())) {
						huaList.add(room.getDingHunPai());
						if (huaList.containsAll(Cnst.PAI_HUA_CXQD)
								|| huaList.containsAll(Cnst.PAI_HUA_MLJZ)) {
							changeGangFen(players, player, 2);
						}
					} else {
						if (huaList.containsAll(Cnst.PAI_HUA_CXQD)
								|| huaList.containsAll(Cnst.PAI_HUA_MLJZ)) {
							changeGangFen(players, player, 2);
						}
					}
				}
			}
		}

		//FIXME
		//统计玩家各项数据 庄次数 胡的次数 特殊胡的次数 自摸次数 点炮次数 胡牌类型 具体番数 各个分数统计 
		if(room.getHuangZhuang() != null && room.getHuangZhuang() == true){
			//荒庄不荒杠
			for(Player p : players){
				p.setScore(p.getScore()+p.getGangScore());
			}
		}else{ //正常结算
			int fen = MahjongUtils.checkHuFenInfo(players,room); // 检查胡牌玩家的分数.包含杠分
			// 计分方式：1,大包 2 出冲包三家 3陪冲 4不出冲			
			if (room.getScoreType() == Cnst.DA_BAO) { //是大包
				if(ziMo){
					for (Player player : players){//赢家是自摸 
						if(player.getIsHu()){ 
							player.setThisScore(fen*3);
							player.setScore(player.getScore() + player.getThisScore() + player.getGangScore());
						}else {
							player.setThisScore(-fen);
							player.setScore(player.getScore() + player.getThisScore() + player.getGangScore());
						}
					}
				}else{ //赢家不是自摸,点炮的大包,包杠分
					//统计杠分
					Integer gangFen = 0;
					for(Player p:players){
						if(p.getIsDian()){
							continue;
						}
						List<Action> actionList = p.getActionList();
						if(actionList != null && actionList.size() != 0){
							for (Action action : actionList) {
								if(action.getType() == Cnst.ACTION_TYPE_DIANGANG || action.getType() == Cnst.ACTION_TYPE_PENGGANG){ //明杠1分
									p.setGangScore(p.getGangScore()+3);
									gangFen -= 3;
								}else if(action.getType() == Cnst.ACTION_TYPE_ANGANG){ //暗杠2分
									p.setGangScore(p.getGangScore()+6);
									gangFen -= 6;
								} 
							}
						}
						List<Integer> huaList = MahjongUtils.getNewList(p.getHuaList());
						if(huaList.size()>=3){
							if(Cnst.PAI_HUA.contains(room.getDingHunPai())){
								huaList.add(room.getDingHunPai());
								if(huaList.containsAll(Cnst.PAI_HUA_CXQD) || huaList.containsAll(Cnst.PAI_HUA_MLJZ)){
									p.setGangScore(p.getGangScore()+6);
									gangFen -= 6;
								}
							}else{
								if(huaList.containsAll(Cnst.PAI_HUA_CXQD) || huaList.containsAll(Cnst.PAI_HUA_MLJZ)){
									p.setGangScore(p.getGangScore()+6);
									gangFen -= 6;
								}
							}
						}
					}
					for(Player player : players){
						if(player.getIsHu()){ //胡的人分,需要减去其他玩家赢的杠分
							player.setThisScore(fen * 3 + 1);
							player.setScore(player.getScore() + player.getThisScore()+player.getGangScore());
						}else{
							if(player.getIsDian()){//点炮的人 大包,包杠分
								player.setGangScore(gangFen);
								player.setThisScore(- fen * 3 - 1);
								player.setScore(player.getScore() + player.getThisScore()+gangFen);
							}else{
								player.setScore(player.getScore()+player.getGangScore());
							}
						}					
					}			
				}
			}
			else if(room.getScoreType() == Cnst.CHU_CHONG_BAOSANJIA) { //包三家,但是不包杠分.			
				if(ziMo){//赢家是自摸 
					for(Player player : players){
						if(player.getIsHu()){ //赢家所得分数-其他玩家赢的杠分
							player.setThisScore(fen * 3);
							player.setScore(player.getScore() + player.getThisScore() + player.getGangScore());
						}else { //其他玩家分数
							player.setThisScore(- fen);
							player.setScore(player.getScore() + player.getThisScore() + player.getGangScore());
						}
					}						
				}else{ //赢家不是自摸
					for(Player player:players){
						if(player.getIsHu()){ 
							player.setThisScore(fen * 3+ 1);
							player.setScore(player.getScore() + player.getThisScore()+ player.getGangScore());
						}else{
							if(player.getIsDian()){//点炮的人 ,点炮的包三家,不包杠分
								player.setThisScore( - fen * 3 - 1);
								player.setScore(player.getScore() + player.getThisScore()+ player.getGangScore());
							}else{
								player.setThisScore(0);
								player.setScore(player.getScore() + player.getGangScore() + player.getThisScore());
							}
						}	
					}						
				}			
			} else if(room.getScoreType() == Cnst.PAI_CHONG){ //可以自摸,各付各的
				if(ziMo){//赢家是自摸 
					for(Player player : players){
						if(player.getIsHu()){ 
							player.setThisScore(fen * 3 );
							player.setScore(player.getScore() + player.getThisScore()+ player.getGangScore());
						}else {
							player.setThisScore( - fen);
							player.setScore(player.getScore() + player.getThisScore()+ player.getGangScore());
						}
					}					
				}else{ //赢家不是自摸
					for(Player player : players){
						if(player.getIsHu()){ 
							player.setThisScore(fen * 3+ 1);
							player.setScore(player.getScore() + player.getThisScore()+player.getGangScore());
						}else{
							if(player.getIsDian()){
								player.setThisScore( - fen  - 1);
								player.setScore(player.getScore() + player.getThisScore()+ player.getGangScore());
							}else{
								player.setThisScore( - fen);
								player.setScore(player.getScore() + player.getThisScore()+ player.getGangScore());
							}	
						}
					}						
				}			
			}else if(room.getScoreType() == Cnst.BU_CHU_CHONG){ //只能自摸,各付各的
				for (Player player : players) {
					if(player.getIsHu()){ 
						player.setThisScore(fen * 3);
						player.setScore(player.getScore() + player.getThisScore() + player.getGangScore());
					}else {
						player.setThisScore(- fen);
						player.setScore(player.getScore() + player.getThisScore() + player.getGangScore());
					}
				}
			}		
			if(room.getWinPlayerId().equals(room.getZhuangId())){
				//庄不变
			}else{
				//下个人坐庄
				int index = -1;
				Long[] playIds = room.getPlayerIds();
				for(int i=0;i<playIds.length;i++){
					if(playIds[i].equals(room.getZhuangId())){
						index = i+1;
						if(index == 4){
							index = 0;
						}
						break;
					}
				}
				room.setZhuangId(playIds[index]);
				room.setCircleWind(index+1);
				
				//不是第一局,并且圈风是东风 ,证明是下一圈了.
				if(room.getXiaoJuNum() != 1 && room.getCircleWind() == Cnst.WIND_EAST){
					room.setTotolCircleNum(room.getTotolCircleNum() == null ? 1:room.getTotolCircleNum()+1);
					room.setLastNum(room.getCircleNum() - room.getTotolCircleNum());
				}
			}
		}
		
	
		// 更新redis
		RedisUtil.setPlayersList(players);
		
		// 添加小结算信息
		List<Integer> xiaoJS = new ArrayList<Integer>();
		for (Player p : players) {
			xiaoJS.add(p.getThisScore()+p.getGangScore());
		}
		room.addXiaoJuInfo(xiaoJS);
		// 初始化房间
		room.initRoom();
		RedisUtil.updateRedisData(room, null);
		// 写入文件
		List<Map<String, Object>> userInfos = new ArrayList<Map<String, Object>>();
		for (Player p : players) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("userId", p.getUserId());
			map.put("score", p.getThisScore()+p.getGangScore());
			map.put("pais", p.getCurrentMjList());
			map.put("huaList", p.getHuaList());
			map.put("gangScore", p.getGangScore());
			if(p.getIsHu()){
				map.put("isWin", 1);
				map.put("winInfo", p.getFanShu());
			}else{
				map.put("isWin", 0);
			}
			if(p.getIsDian()){
				map.put("isDian", 1);
			}else{
				map.put("isDian", 0);
			}
			if(p.getActionList() != null && p.getActionList().size() > 0){
				List<Object> actionList = new ArrayList<Object>();
				for(Action action : p.getActionList()){
					if(action.getType() == Cnst.ACTION_TYPE_CHI){
						Map<String,Integer> actionMap = new HashMap<String, Integer>();
						actionMap.put("action", action.getActionId());
						actionMap.put("extra", action.getExtra());
						actionList.add(actionMap);
						
					}else if(action.getType() == Cnst.ACTION_TYPE_ANGANG){
						Map<String,Integer> actionMap = new HashMap<String, Integer>();
						actionMap.put("action", -2);
						actionMap.put("extra", action.getActionId());
						actionList.add(actionMap);
					}else{
						actionList.add(action.getActionId());
					}
				}
				map.put("actionList", actionList);
			}			
			userInfos.add(map);
		}
		JSONObject info = new JSONObject();
		info.put("lastNum", room.getLastNum());
		info.put("userInfo", userInfos);
		BackFileUtil.save(100102, room, null, info,null);
		// 小结算 存入一次回放
		BackFileUtil.write(room);

		// 大结算判定 (玩的圈数等于选择的圈数)
		if (room.getTotolCircleNum() == room.getCircleNum()) {
			// 最后一局 大结算
			room = RedisUtil.getRoomRespByRoomId(roomId);
			room.setState(Cnst.ROOM_STATE_YJS);
			RedisUtil.updateRedisData(room, null);
			// 这里更新数据库吧
			RoomUtil.updateDatabasePlayRecord(room);
		}
	}

	public static void changeGangFen(List<Player> players, Player player,Integer fen) {
		player.setGangScore(player.getGangScore() + 3 * fen);
		//其余人减分
		for(Player p:players){
			if(p == player){
				continue;
			}else{
				p.setGangScore(p.getGangScore() - fen);
			}
		}
	}
}
