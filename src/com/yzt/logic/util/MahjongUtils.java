package com.yzt.logic.util;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yzt.logic.mj.domain.Action;
import com.yzt.logic.mj.domain.Player;
import com.yzt.logic.mj.domain.RoomResp;
import com.yzt.logic.util.JudegHu.checkHu.Hulib;
import com.yzt.logic.util.JudegHu.checkHu.TableMgr;
import com.yzt.logic.util.redis.RedisUtil;

/**
 * 
 * @author wsw_007
 *
 */
public class MahjongUtils {

	static {
		// 加载胡的可能
		TableMgr.getInstance().load();
	}
	
	
	/**
	 * 获得所需要的牌型(干里干) 并打乱牌型
	 * 
	 * @return
	 */
	public static List<Integer> getPais(RoomResp room) {
		// 1-9万 ,10-18饼,19-27条,32红中.
		ArrayList<Integer> pais = new ArrayList<Integer>();
		if(room.getHuaType() == Cnst.YOUHUA){
			for(int i=180;i<=187;i++){
				pais.add(i);
			}
		}
		for (int j = 0; j < 4; j++) {
			for (int i = 1; i <= 34; i++) {
				pais.add(i);
			}
		}
		// 2.洗牌
		Collections.shuffle(pais);
		return pais;
		
	}

	
	/**
	 * 删除用户指定的一张牌
	 * 
	 * @param currentPlayer
	 * @return
	 */
	public static void removePai(Player currentPlayer, Integer action) {
		Iterator<Integer> pai = currentPlayer.getCurrentMjList().iterator();
		while (pai.hasNext()) {
			Integer item = pai.next();
			if (item.equals(action)) {
				pai.remove();
				break;
			}
		}
	}

	
	/**
	 * 
	 * @param room
	 *            房间
	 * @param currentPlayer
	 *            当前操作的玩家
	 * @return 返回需要通知的操作的玩家ID
	 */
	public static Long nextActionUserId(RoomResp room, Long lastUserId) {
		Long[] playerIds = room.getPlayerIds();

		for (int i = 0; i < playerIds.length; i++) {
			if (lastUserId == playerIds[i]) {
				if (i == playerIds.length - 1) { // 如果是最后 一个,则取第一个.
					return playerIds[0];
				} else {
					return playerIds[i + 1];
				}
			}
		}
		return -100l;
	}

	/**
	 * 定混牌//上滚定混
	 * 
	 * @param pais
	 *            第一次发牌后剩余的牌
	 * @return
	 */
	public static Integer dingHunPai(List<Integer> pais,RoomResp room) {
		List<Integer> faPai = faPai(pais,1);
		Integer hunPai = faPai.get(0);
//		hunPai = 34;
		room.setDingHunPai(hunPai);
		if(hunPai == 31){
			hunPai = 28;
		}else if(hunPai == 34){
			hunPai = 32;
		} else if(hunPai == 180 ||hunPai == 184 ){
			hunPai = 29;
		}else if(hunPai == 181 ||hunPai == 185 ){
			hunPai = 30;
		}else if(hunPai == 182 ||hunPai == 186 ){
			hunPai = 31;
		}else if(hunPai == 183 ||hunPai == 187 ){
			hunPai = 28;
		}else if (hunPai % 9 == 0) {// 上滚定混
			hunPai = hunPai - 8;
		} else { // 正常混牌
			hunPai = hunPai + 1;
		}
		return hunPai;
	}
 

	/**
	 * 干里干胡牌规则 1,必须有幺九牌 2,要求三门齐或清一色(红中不算们,单门+红中胡牌算清一色) 3,必须有刻牌(三个一样,或者四个一样)
	 * 4,必须开门才能胡牌(吃,碰,杠有一个算开门) 5,每一局只能胡一个玩家. 6,胡的优先级(胡>杠>碰>吃)
	 * 
	 *
	 * @return true 为可以胡牌牌型
	 */

	public static boolean checkHuRule(Player p,RoomResp room ,Integer pai,Integer type) {
		if(room.getScoreType() == 4 && type == Cnst.CHECK_TYPE_BIERENCHU){
			//不出冲
			return false;
		}
		Integer hunNum = hunNum(p, room.getHunPai());
		if(hunNum == 4){
			return true;
		}
		
		if(type == Cnst.CHECK_TYPE_BIERENCHU && p.getGuoHu()!=null && p.getGuoHu() == true && p.getGuoHuPai() == pai){
			return false;
		}
		
		boolean piHu = checkPiHu(p, room, pai, type);
		if(!piHu){
			return false;
		}
		
		List<Integer> newList = getNewList(p.getCurrentMjList());
		if(type == Cnst.CHECK_TYPE_ZIJIMO){
			pai = null;
		}
		if(pai != null){
			newList.add(pai); //检测要带上别人打出的牌
		}
		
//		if(checkQiDui(p,newList, room.getHunPai())){
//			return false;
//		}
		
		if(benHunHu(newList)){
			return true;
		}
		
		
		if(baiDaHuYiQie(room,p,pai)){
			if(Cnst.CHECK_TYPE_ZIJIMO == type){
				return true;
			}
			else{
				//检测除了白搭 是否可以平胡
				if(checePingHuNoHunDiao(room,p,pai)){
					return true;
				}else{
					return false;
				}
			}
		}
		
		
				
		return piHu;		
	}

	/***
	 * 检测在满足混吊的情况下会不会有穷胡
	 * @param room
	 * @param p
	 * @param pai
	 * @return
	 */
	public static boolean checePingHuNoHunDiao(RoomResp room, Player p,
			Integer pai) {
		List<Integer> newList = getNewList(p.getCurrentMjList());
		Set<Integer> checkSet = new HashSet<Integer>();
		for(Integer i:newList){
			if(room.getHunPai() == i){
				continue;
			}
			checkSet.add(i);
		}
		if(pai != null){
			newList.add(pai);
		}else{
			return false;
		}
		//除去 一个混 一个除这牌 是否可以胡牌 如果可以那么表示 玩家可以胡混吊也可以穷胡(如果手牌中包含相同牌 也要移除 只要不是最后一张摸的或别人打的即可)
		//先移除一个混排
		for(int i=0;i<newList.size();i++){
			if(newList.get(i) == room.getHunPai()){
				newList.remove(i);
				break;
			}
		}
		for(Integer i:checkSet){
			int[] checkPai = getCheckHuPai(newList,null);
			checkPai[i-1] = checkPai[i-1] - 1;
			if(Hulib.getInstance().get_hu_info(checkPai, 34, room.getHunPai() - 1)){
				return true;
			}
		}
		return false;
		
	}


	/**
	 * 检测屁胡
	 * @param p
	 * @param room
	 * @param pai
	 * @param type
	 * @return
	 */
	private static boolean checkPiHu(Player p, RoomResp room, Integer pai,
			Integer type) {
		int[] huPaiZu = new int[34];
		if(type == Cnst.CHECK_TYPE_ZIJIMO){
			huPaiZu = getCheckHuPai(p.getCurrentMjList(), null);
			 if (Hulib.getInstance().get_hu_info(huPaiZu, 34, room.getHunPai() - 1)) { 
				 return true;
				}
		}else{
			huPaiZu = getCheckHuPai(p.getCurrentMjList(), pai);
			if (Hulib.getInstance().get_hu_info(huPaiZu, 34, room.getHunPai() - 1)) { 
				return true;
			}
		}
		return false;
	}
	
	/***
	 * 检测七对
	 * @param p
	 * @param newList
	 * @param pai
	 * @return
	 */
	public static boolean checkQiDui(Player p,List<Integer> newList,Integer pai) {
		if(newList.size()!=14){
			return false;
		}
		Integer hunNum = hunNum(p, pai);
		List<Integer> noHunList = getNoHunList(newList, pai);
		int oneNum=0;
		int[] checkHuPai = getCheckHuPai(noHunList, null);
		for (int i : checkHuPai) {
			if(i==1 || i==3){
				oneNum++;
			}
		}
		if(oneNum<=hunNum){
			return true;
		}
		return false;
	}

	/**
	 * 检测动作集合
	 * @param p
	 * @param pai
	 * @param room
	 * @param type
	 * @param checkChi 自己打的牌true,不提示吃.
	 * @return
	 */
	public static List<Integer> checkActionList(Player p, Integer pai, RoomResp room,Integer type,Boolean checkChi) {
		Integer hunPai = room.getHunPai();
		List<Integer> actionList = new ArrayList<Integer>();
		
		
		if(Cnst.CHECK_TYPE_HAIDIANPAI == type){
			if (checkHuRule(p,room,pai,Cnst.CHECK_TYPE_ZIJIMO)) {
				actionList.add(500);
			}
			return actionList;
		}
		if(Cnst.CHECK_TYPE_QIANGGANG == type){
			if (checkHuRule(p,room,pai,Cnst.CHECK_TYPE_BIERENCHU)) {
				actionList.add(500);										
			}
			if(actionList.size() > 0){
				actionList.add(0);
			}
			return actionList;
		}
		
		if ( room.getChiType()!=1 && checkChi && type != Cnst.CHECK_TYPE_ZIJIMO  && checkChi(p, pai,hunPai)) {
			List<Integer> c = chi(p, pai,hunPai);
			actionList.addAll(c);
		}
		if ( type != Cnst.CHECK_TYPE_ZIJIMO  && checkPengGuo(p,pai) && checkPeng(p, pai,hunPai)) {
			Integer peng = peng(p, pai);
			//定搭牌 
			if(pai == room.getDingHunPai()){
				actionList.add(pai+90);
			}else{
				actionList.add(peng);
			}		
		}
		
		//1,不是自摸,检测别人出牌的时候,能不能点杠.
		if ( Cnst.CHECK_TYPE_ZIJIMO != type && checkGang(p, pai)) {
			Integer gang = gang(p, pai,false);
			actionList.add(gang);
		}
		//2,自摸的时候,检测能不能暗杠.
		if( Cnst.CHECK_TYPE_ZIJIMO == type ){
			List<Integer> checkAnGang = checkAnGang(p,hunPai,room);
			for (int i = 0; i < checkAnGang.size(); i++) {
				actionList.add(checkAnGang.get(i));
			}
		}
		//3,自摸的时候,检测能不能碰杠
		if(Cnst.CHECK_TYPE_ZIJIMO == type){
			List<Integer> pengGang = checkPengGang(p, pai);
			if(pengGang.size() != 0){
				actionList.addAll(pengGang);
			}
			
		}
		
		if (checkHuRule(p,room,pai,type)) {
			actionList.add(500);								
		}	
			
		if (actionList.size() != 0) {
			actionList.add(0);
		}else{
			//没有动作 只能出牌
			if(type == Cnst.CHECK_TYPE_ZIJIMO){
				actionList.add(501);
			}		
		}		
		return actionList;
	}

	/**
	 * 检测能不能碰完以后再开杠.
	 * @param p
	 * @return
	 */
	private static List<Integer> checkPengGang(Player p, Integer pai) {
		List<Action> actionList = p.getActionList();//统计用户所有动作 (吃碰杠等)
		List<Integer> newList = getNewList(p.getCurrentMjList());
		List<Integer> gangList = new ArrayList<Integer>();
		for (int i = 0; i < actionList.size(); i++) {
			if(actionList.get(i).getType() == 2){
				for(int m=0;m<newList.size();m++){
					if(newList.get(m) == actionList.get(i).getExtra()){
						gangList.add(newList.get(m)+90);
					}
				}
			}
		}
		return gangList;
	}

	/**
	 * 混牌的数量
	 * 
	 * @return
	 */
	public static Integer hunNum(Player p, Integer pai) {
		Integer num = 0;
		for (int i = 0; i < p.getCurrentMjList().size(); i++) {
			if (p.getCurrentMjList().get(i) == pai) {
				num = num + 1;
			}
		}
		return num;
	}

	

	/**
	 * 牌型是否有刻牌(三个一样或者四个一样)
	 * 
	 * @param p
	 * @return true 是
	 */
	public static boolean isKePai(Player p,Integer hunPai,List<Integer> newList) {
		//检测动作里面是否有刻
		List<Action> actionList = p.getActionList();
		//1吃   2碰  3点杠 4碰杠 5暗杠 
		for (Action action : actionList) {
			if(action.getType()!=1){
				return true;
			}
		}
		//检测手中有没有刻
		Set<Integer> distinct=new HashSet<Integer>();
		for (Integer integer : newList) {
			distinct.add(integer);
		}
		//手牌中是否有3张,这3张移除必须能胡才行 比如12333
		Integer hunNum = hunNum(p, hunPai);
		int num=0;
		for (Integer distinctPai : distinct) {
			num=0;
			for (Integer p1 : newList) {
				if(distinctPai.equals(p1)){
					num++;
				}
			}
			if(num>=3){					
				int[] huPaiZu = getCheckHuPai(newList,null); 
				//将这3张牌移除
				huPaiZu[distinctPai-1] = num - 3;
				if (Hulib.getInstance().get_hu_info(huPaiZu, 34, hunPai - 1)) {
					return true;
				}
			}else if(hunNum + num >=3){
				int[] huPaiZu = getCheckHuPai(newList,null);
				huPaiZu[hunPai - 1] =hunNum -  (3 - num);
				huPaiZu[distinctPai-1] = 0;
				if (Hulib.getInstance().get_hu_info(huPaiZu, 34, hunPai - 1)) {
					return true;
				}
			}
		}
		return false;
	}


	/***
	 * 根据出的牌 设置下个动作人和玩家
	 * @param players
	 * @param room
	 * @param pai
	 */
	public static void getNextAction(List<Player> players, RoomResp room, Integer pai){
		Integer maxAction = 0;
		Long nextActionUserId = -1L;
		List<Integer> nextAction = new ArrayList<Integer>();
		int index = -1;
		Long[] playIds = room.getPlayerIds();
		for(int i=0;i<playIds.length;i++){
			if(playIds[i].equals(room.getLastChuPaiUserId())){
				index = i+1;
				if(index == 4){
					index = 0;
				}
				break;
			}
		}
		Long xiaYiJia = playIds[index];
		//从下一家开始检测 多胡的话 会按顺序来
		Player[] checkList = new Player[4];
		for(int i=0;i<players.size();i++){
			if(i == index){
				checkList[0] = players.get(i);
			}
			if(i<index){
				checkList[4-(index-i)] = players.get(i);
			}
			if(i>index){
				checkList[i-index] = players.get(i);
			}
		}
		for(Player p:checkList){
			if(!room.getGuoUserIds().contains(p.getUserId())){
				//玩家没点击过 或者不是 出牌的人  吃只检测下个人
				List<Integer> checkActionList;
				if(p.getUserId().equals(xiaYiJia)){
					checkActionList = checkActionList(p, pai, room,Cnst.CHECK_TYPE_BIERENCHU,true);
				}else{
					checkActionList = checkActionList(p, pai, room,Cnst.CHECK_TYPE_BIERENCHU,false);
				}
				
				if(checkActionList.size() == 0){
					//玩家没动作 
					room.getGuoUserIds().add(p.getUserId());
				}else{
					Collections.sort(checkActionList);
					if(checkActionList.get(checkActionList.size()-1) > maxAction){
						nextActionUserId = p.getUserId();
						nextAction = checkActionList;
						maxAction = checkActionList.get(checkActionList.size()-1);
					}
				}
			}
		}
		//如果都没可执行动作 下一位玩家请求发牌
		if(maxAction == 0){
			nextAction.add(-1);
			room.setNextAction(nextAction);
			//取到上个出牌人的角标 下一位来发牌
			room.setNextActionUserId(xiaYiJia);
		}else{
			room.setNextAction(nextAction);
			room.setNextActionUserId(nextActionUserId);
		}
	
	}

	/**
	 * 检查玩家能不能碰
	 * 
	 * @param p
	 * @param Integer
	 *            peng 要碰的牌
	 * @return
	 */
	public static boolean checkPeng(Player p, Integer peng,Integer hunPai) {
		int num = 0;
		for (Integer i : p.getCurrentMjList()) {
			if(i == peng){
				num++;
			}
		}
		if (num >= 2) {
			return true;
		}
		return false;
	}

	/**
	 * //与吃的那个牌能组合的List
	 * @param p
	 * @param chi
	 * @return
	 */
	public static List<Integer> reChiList(Integer action ,Integer chi){
		ArrayList<Integer> arrayList = new ArrayList<Integer>();
		for (int i = 35; i <= 56; i++) {
			if(i == action ){
				int[] js = Cnst.chiMap.get(action);
				for (int j = 0; j < js.length; j++) {
					if(js[j] != chi){
						arrayList.add(js[j]);
					}
				}
			}
		}
		return arrayList; 
	}
	
	/**
	 * 执行动作吃!
	 * 返回原本手里的牌
	 * @param p
	 * @param chi
	 * @return
	 */
	public static List<Integer> chi(Player p, Integer chi, Integer hunPai) {
		List<Integer> shouPai = getNoHunList(p.getCurrentMjList(), hunPai);
		Set<Integer> set = new HashSet<Integer>();
		List<Integer> reList = new ArrayList<Integer>();
		boolean a = false; // x<x+1<x+2
		boolean b = false; // x-1<x<x+1
		boolean c = false; // x-2<x-1<x

		// 万
		if (chi < 10) { // 基数34
			List<Integer> arr = new ArrayList<Integer>();
			arr.add(chi + 1);
			arr.add(chi + 2);
			if (shouPai.containsAll(arr)) {
				a = true;
			}
			List<Integer> arr1 = new ArrayList<Integer>();
			arr1.add(chi - 1);
			arr1.add(chi + 1);
			if (shouPai.containsAll(arr1)) {
				b = true;
			}
			List<Integer> arr2 = new ArrayList<Integer>();
			arr2.add(chi - 1);
			arr2.add(chi - 2);
			if (shouPai.containsAll(arr2)) {
				c = true;
			}

			if (a && chi != 9 && chi != 8) {
				set.add(34 + chi);
			}
			if (b && chi != 9) {
				set.add(33 + chi);
			}
			if (c) {
				set.add(32 + chi);
			}

			// 饼
		} else if (chi >= 10 && chi <= 18) { // 基数32
			List<Integer> arr = new ArrayList<Integer>();
			arr.add(chi + 1);
			arr.add(chi + 2);
			if (shouPai.containsAll(arr)) {
				a = true;
			}
			List<Integer> arr1 = new ArrayList<Integer>();
			arr1.add(chi - 1);
			arr1.add(chi + 1);
			if (shouPai.containsAll(arr1)) {
				b = true;
			}
			List<Integer> arr2 = new ArrayList<Integer>();
			arr2.add(chi - 1);
			arr2.add(chi - 2);
			if (shouPai.containsAll(arr2)) {
				c = true;
			}
			if (a & chi != 18 && chi != 17) {
				set.add(32 + chi);
			}
			if (b && chi != 10 && chi != 18) {
				set.add(31 + chi);
			}
			if (c && chi != 10 && chi != 11) {
				set.add(30 + chi);
			}
			// 条
		} else if (chi >= 19 && chi <= 27) { // 基数30
			List<Integer> arr = new ArrayList<Integer>();
			arr.add(chi + 1);
			arr.add(chi + 2);
			if (shouPai.containsAll(arr)) {
				a = true;
			}
			List<Integer> arr1 = new ArrayList<Integer>();
			arr1.add(chi - 1);
			arr1.add(chi + 1);
			if (shouPai.containsAll(arr1)) {
				b = true;
			}
			List<Integer> arr2 = new ArrayList<Integer>();
			arr2.add(chi - 1);
			arr2.add(chi - 2);
			if (shouPai.containsAll(arr2)) {
				c = true;
			}
			if (a & chi != 26 && chi != 27) {
				set.add(30 + chi);
			}
			if (b && chi != 19 && chi != 27) {
				set.add(29 + chi);
			}
			if (c && chi != 19 && chi != 20) {
				set.add(28 + chi);
			}
		}
		reList.addAll(set);
		return reList;
	}
	/**
	 * 执行动作杠
	 * 
	 * @param p
	 * @param gang
	 * @return
	 */
	public static Integer gang(Player p, Integer gang, Boolean pengGang) {
		List<Integer> shouPai = p.getCurrentMjList();
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();
		
		if(pengGang){
			List<Action> actionList = p.getActionList();//统计用户所有动作 (吃碰杠等)
			for (int i = 0; i < actionList.size(); i++) {
				if(actionList.get(i).getType() == 2 && actionList.get(i).getExtra() == gang){
					return 90 + gang;
				}
			}
		}

		for (Integer item : shouPai) {
			if (map.containsKey(item)) {
				map.put(item, map.get(item).intValue() + 1);
			} else {
				map.put(item, new Integer(1));
			}
		}

		Iterator<Integer> keys = map.keySet().iterator();
		while (keys.hasNext()) {
			Integer key = keys.next();
			if (map.get(key).intValue() == 3) { // 控制有几个重复的
				// System.out.println(key + "有重复的:" + map.get(key).intValue() +
				// "个 ");
				if (key == gang) {
					return 90 + gang;
				}
			}
		}

		return -100;
	}

	/**
	 * 执行动作碰
	 * 
	 * @param p
	 * @param peng
	 * @return 行为编码
	 */
	public static Integer peng(Player p, Integer peng) {
		return 56 + peng;
	}

	/**
	 *  * 检测玩家能不能吃.10 与19特殊处理
	 * @param p
	 * @param chi
	 * @param hunPai 不能吃
	 * @return
	 */
	public static boolean checkChi(Player p, Integer chi,Integer hunPai) {
		List<Integer> list = getNoHunList(p.getCurrentMjList(), hunPai);
		boolean isChi = false;
		List<Integer> arr = new ArrayList<Integer>();
		arr.add(chi + 1);
		arr.add(chi + 2);
		if (list.containsAll(arr)) {
			isChi = true;
		}
		List<Integer> arr1 = new ArrayList<Integer>();
		List<Integer> arr2 = new ArrayList<Integer>();
		if (chi != 10 && chi != 19) {
			arr1.add(chi - 1);
			arr1.add(chi + 1);
			if (list.containsAll(arr1)) {
				isChi = true;
			}
			arr2.add(chi - 1);
			arr2.add(chi - 2);
			if (list.containsAll(arr2)) {
				isChi = true;
			}
		}
		return isChi;
	}

	/**
	 * 执行暗杠
	 * 
	 * @param p
	 * @return 返回杠的牌
	 */
	public static Integer anGang(Player p) {
		List<Integer> shouPai = p.getCurrentMjList();
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();

		for (Integer item : shouPai) {
			if (map.containsKey(item)) {
				map.put(item, map.get(item).intValue() + 1);
			} else {
				map.put(item, new Integer(1));
			}
		}

		Iterator<Integer> keys = map.keySet().iterator();
		Integer gang = 0;
		while (keys.hasNext()) {
			Integer key = keys.next();
			if (map.get(key).intValue() == 4) { // 控制有几个重复的
				// System.out.println(key + "有重复的:" + map.get(key).intValue() +
				// "个 ");
				gang = key;
			}
		}

		Iterator<Integer> iter1 = p.getCurrentMjList().iterator();
		while (iter1.hasNext()) {
			Integer item = iter1.next();
			if (item == gang) {
				iter1.remove();
			}
		}
		return gang + 90;
	}

	/**
	 * 检查能不能暗杠
	 * 
	 * @param p
	 * @param gang
	 * @return
	 */
	public static List<Integer> checkAnGang(Player p,Integer hunPai,RoomResp room) {
		List<Integer> anGangList = new ArrayList<Integer>();
		List<Integer> shouPai = p.getCurrentMjList();
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();

		for (Integer item : shouPai) {
			if (map.containsKey(item)) {
				map.put(item, map.get(item).intValue() + 1);
			} else {
				map.put(item, new Integer(1));
			}
		}

		Iterator<Integer> keys = map.keySet().iterator();
		while (keys.hasNext()) {
			Integer key = keys.next();
			if (map.get(key).intValue() == 4 && key != hunPai) { // 控制有几个重复的
				 anGangList.add(key+90);
			}
			if(map.get(key).intValue() == 3 && key != hunPai && key == room.getDingHunPai()){
				anGangList.add(key+90);
			}
		}
		return anGangList;
	}

	/**
	 * 检测玩家能不能杠
	 * 1,明杠,2暗杠,3 点杠
	 * @param p
	 * @return
	 */
	public static boolean checkGang(Player p, Integer gang) {
		List<Integer> shouPai = p.getCurrentMjList();
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();

		for (Integer item : shouPai) {
			if (map.containsKey(item)) {
				map.put(item, map.get(item).intValue() + 1);
			} else {
				map.put(item, new Integer(1));
			}
		}

		Iterator<Integer> keys = map.keySet().iterator();
		while (keys.hasNext()) {
			Integer key = keys.next();
			if (map.get(key).intValue() == 3) { // 控制有几个重复的;
				if (key == gang) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 
	 * @param mahjongs
	 *            房间内剩余麻将的组合
	 * @param num
	 *            发的张数
	 * @return
	 */
	public static List<Integer> faPai(List<Integer> mahjongs, Integer num) {
		if (mahjongs.size() == 0) {
			return null;
		}
		List<Integer> result = new ArrayList<>();
		for (int i = 0; i < num; i++) {
			result.add(mahjongs.get(i));
			mahjongs.remove(i);
		}
		return result;
	}
	
	
	
	/**
	 * 返回一个新的集合
	 * @param old
	 * @return
	 */
	public static List<Integer> getNewList(List<Integer> old) {
		List<Integer> newList = new ArrayList<Integer>();
		if (old != null && old.size() > 0) {
			for (Integer pai : old) {
				newList.add( pai );
			}
		}
		return newList;
	}

	/**
	 * 丹阳推到胡规则 返回的是分
	 * 
	 * @param players
	 * @param room
	 * @return
	 */
	public static int checkHuFenInfo(List<Player> players,RoomResp room) {
		Player p = null;
		//胡牌就是1分
		int fen = 1;
		List<Integer> winInfo = new ArrayList<Integer>();
		for (Player player : players) {
			if (player.getIsHu()) {
				player.setHuNum(player.getHuNum() + 1);
				p = player;
			}
		}
		Integer hunNum = hunNum(p, room.getHunPai());		
		//打出花牌的个数.一个1分.
		fen += p.getHuaList().size();
		//四个百搭直接胡6分
		for(Action action:p.getActionList()){
			if(action.getType() == Cnst.ACTION_TYPE_ANGANG && action.getExtra() == room.getHunPai()){
//				winInfo.add(Cnst.SIHUNHU);
				p.setFanShu(winInfo);
				fen += 5;
				return fen ;
			}
		}
//		if(room.getZhuangId().equals(p.getUserId())){
//			winInfo.add(Cnst.ZHUANGJIA);
//		}
		//自摸加2分
		if(p.getIsZiMo()){
			fen += 2;
			winInfo.add(Cnst.ZIMO);
		}else{
			winInfo.add(Cnst.DIANPAO);
		}
		
		
		//杠开加1分
		if(room.getLastAction()>=90 && room.getLastAction() <=127 && p.getIsZiMo()){
			fen += 1;
			winInfo.add(Cnst.GANGKAI);
		}
		//无搭或者搭牌算本牌用
		List<Integer> newList = getNewList(p.getCurrentMjList());
		boolean wuDa = false;
		if(hunNum == 0 || benHunHu(newList)){
			wuDa = true;
			fen += 1;
			winInfo.add(Cnst.WUDA);
		}
		//刻牌 
		List<Action> actionList = p.getActionList();
		if(actionList != null && actionList.size()>0){
			for(Action action:actionList){
				if(action.getType() == Cnst.ACTION_TYPE_PENG){
					if(action.getExtra()>=28 && action.getExtra() <= 34){
						fen += 1;
						winInfo.add(Cnst.KEPAI);
					}
				}
			}
		}
		
		Integer keNum = keNum(room, p, hunNum);
		if(keNum > 0){
			for(int i=1;i<=keNum;i++){
				fen += 1;
				winInfo.add(Cnst.KEPAI);
			}
		}
		//杠后炮，抢杠胡
		if( (room.getIsQiangGangHu() != null && room.getIsQiangGangHu())|| (room.getChiPengGang() != null && room.getChiPengGang() == 3 && !p.getIsZiMo())){
//			fen += 1;
			winInfo.add(Cnst.DIANPAOHU);
		}
		boolean baDaHu = baiDaHuYiQie(room, p,null);
		//百搭单调, 百搭胡一切
		if(!wuDa && p.getIsZiMo() && baDaHu){
			fen += 1;
			winInfo.add(Cnst.BADAHU);
		}
	
//		if(!winInfo.contains(4)&&!winInfo.contains(5)&&!winInfo.contains(6)&&!winInfo.contains(7)&&!winInfo.contains(8)){
//			winInfo.add(Cnst.PINGHU);
//		}

		p.setFanShu(winInfo);
		return fen;
	}
	/***
	 * 统计刻牌数量 带混
	 * @param room
	 * @param p
	 * @param newList
	 * @return
	 */
	public static Integer keNum(RoomResp room,Player p,Integer hunNum){
		List<Integer> newList = getNewList(p.getCurrentMjList());
		Integer keNum = 0;
		Integer hunPai = room.getHunPai();
		List<Integer> noHunList = getNoHunList(newList, room.getHunPai());
		int oneNum=0;
		int twoNum=0;
		int[] checkHuPai = getCheckHuPai(noHunList, null);
		for(int i=27 ; i<=33;i++){
			if(checkHuPai[i] == 1){
				oneNum = oneNum + 1 ;
			}
			if(checkHuPai[i] == 2){
				twoNum = twoNum + 1 ;
			}
			if(checkHuPai[i] == 3){
				keNum = keNum + 1;
			}
		}
		if(hunNum == 0){
			return keNum;
		}
		if(twoNum+oneNum > 0){
			if((twoNum + 2*oneNum) <= hunNum){
				//移除 twoNum oneNum 和对应的hunNum 
				for(int i=27;i<=33;i++){
					if(checkHuPai[i] == 1 || checkHuPai[i] == 2){
						checkHuPai[i] = 0 ;
					}
				}
				checkHuPai[hunPai -1] = hunNum - (twoNum + 2*oneNum);
				if(Hulib.getInstance().get_hu_info(checkHuPai, 34, hunPai - 1)){
					keNum = keNum + (twoNum + oneNum);
				}else{
					keNum = keNum + (twoNum + oneNum -1);
				}	
			}else{
				keNum = keNum + (twoNum + oneNum -1);
			}
		}else{
			if(hunNum <= 3){
				return keNum;
			}else{
				checkHuPai[28] = 3;
				checkHuPai[hunPai-1] = 0;
				if(Hulib.getInstance().get_hu_info(checkHuPai, 34, 34)){
					keNum = keNum + 1;
				}else{
					return keNum;
				}			
			}
		}
		return keNum;
	}
	/**
	 * 百搭胡一切
	 * 取出一个胡牌,取出最后发的一张牌.如果能胡,代表可以胡一切.
	 * @param room
	 * @param Huplayer
	 * @param hunPai
	 * @return
	 */
	public static boolean baiDaHuYiQie(RoomResp room,Player p,Integer pai){
		List<Integer> newList = getNewList(p.getCurrentMjList());
		if(pai != null){
			newList.add(pai);
		}
		Integer hunPai = room.getHunPai();
		Integer hunNum = hunNum(p, hunPai);
		if(hunNum == 0){
			return false;
		}
		if(hunNum == 1 && newList.get(newList.size()-1) == hunPai){
			return false;
		}
		Iterator<Integer> iterator = newList.iterator();
		while(iterator.hasNext()){
			Integer hun = iterator.next();
			if(hun == hunPai){
				iterator.remove();
				break;
			}
		}
		int[] removeLastPai = getRemoveLastPai(newList,newList.get(newList.size()-1));
		if(Hulib.getInstance().get_hu_info(removeLastPai, 34, hunPai - 1)){
			return true;
		}
		return false;
		
	}
	
	
	/***
	 * 本混胡
	 * @param newList
	 * @return
	 */
	public static boolean benHunHu(List<Integer> newList){
		int[] checkHu = getCheckHuPai(newList, null);
		if(Hulib.getInstance().get_hu_info(checkHu, 34, 34)){
			return true;
		}
		return false;
	}
	/**
	 * 从牌桌上,把玩家吃碰杠的牌移除.
	 * @param room
	 * @param players
	 */
	
	public static void removeCPG(RoomResp room, List<Player> players) {
		Player currentP = null;
		for (Player p : players) {
			if(p.getUserId().equals(room.getLastChuPaiUserId())){
				currentP = p;
				List<Integer> chuList = p.getChuList();
				Iterator<Integer> iterator = chuList.iterator();
				while(iterator.hasNext()){
					Integer pai = iterator.next();
					if(room.getLastChuPai() == pai ){
						iterator.remove();
						break;
					}
				}
			}
		}
		RedisUtil.updateRedisData(null, currentP);
	}
	
	/***
	 * 移除动作手牌 
	 * @param currentMjList
	 * @param chi
	 * @param action
	 * @param type
	 */
	public static void removeActionMj(List<Integer> currentMjList,List<Integer> chi,Integer action,Integer type){
		Iterator<Integer> it = currentMjList.iterator(); //遍历手牌,删除碰的牌
		switch (type) {
		case Cnst.ACTION_TYPE_CHI:
			int chi1 = 0;
			int chi2 = 0;
			a : while(it.hasNext()){
					Integer x = it.next();
					if(x == chi.get(0) && chi1 == 0){
						it.remove();
						chi1 = 1 ;
					}
					if(x == chi.get(1) && chi2 == 0){
						it.remove();
						chi2 = 1;
					}
					if(chi1 == 1 && chi2 == 1){
						break a;
					}
				}		
			break;
		case Cnst.ACTION_TYPE_PENG:
			int num = 0;
			while(it.hasNext()){
				Integer x = it.next();
			    if(x==action-56){
			        it.remove();
			        num = num + 1;
			        if(num == 2){
			        	break;
			        }
			    }
			}
			break;
		case Cnst.ACTION_TYPE_ANGANG:
			List<Integer> gangPai = new ArrayList<Integer>();
			gangPai.add(action-90);
			currentMjList.removeAll(gangPai);
			break;
		case Cnst.ACTION_TYPE_PENGGANG:
			gangPai = new ArrayList<Integer>();
			gangPai.add(action-90);
			currentMjList.removeAll(gangPai);
			break;
		case Cnst.ACTION_TYPE_DIANGANG:
			gangPai = new ArrayList<Integer>();
			gangPai.add(action-90);
			currentMjList.removeAll(gangPai);
			break;
		default:
			break;
		}
	}
	/***
	 * 获得 检测胡牌的 34位数组 包括摸得或者别人打的那张
	 * @param currentList
	 * @param pai
	 * @return
	 */
	public static int[] getCheckHuPai(List<Integer> currentList,Integer pai){
		int[] checkHuPai = new int[34];
		List<Integer> newList = getNewList(currentList);
		if(pai!=null){
			newList.add(pai);
		}
		for(int i=0;i<newList.size();i++){
			int a = checkHuPai[newList.get(i) - 1];
			checkHuPai[newList.get(i) - 1] = a + 1;
		}
		return checkHuPai;
	}

	/***
	 * 获得 检测胡牌的 34位数组 不包括摸得或者别人打的那张
	 * @param currentList
	 * @param pai
	 * @return
	 */
	public static int[] getRemoveLastPai(List<Integer> currentList,Integer pai){
		int[] checkHuPai = new int[34];
		Boolean hasRemove = false; 
		for(int i=0;i<currentList.size();i++){
			if(currentList.get(i) == pai && !hasRemove){
				hasRemove = true;
				continue;
			}
			int a = checkHuPai[currentList.get(i) - 1];
			checkHuPai[currentList.get(i) - 1] = a + 1;
		}
		return checkHuPai;
	}
	
	/***
	 * 移除混牌 做判断用
	 * @param currList 手牌
	 * @param hunPai 混牌
	 * @return
	 */
	public static List<Integer> getNoHunList(List<Integer> currList,Integer hunPai){
		List<Integer> list = new ArrayList<Integer>();
		for(Integer i:currList){
			if(i == hunPai){
				continue;
			}
			list.add(i);
		}
		return list;
	}
	
	public static boolean checkPengGuo(Player p,Integer pai){
		if(p.getGuoPeng() == false){
			return true;
		}
		if(p.getGuoPengPai() != null && p.getGuoPengPai() == pai){
			return false;
		}
		return true;
	}
	
	public static boolean checkHuGuo(Player p,Integer pai,Integer type){
		if(type != Cnst.CHECK_TYPE_BIERENCHU){
			return false;
		}
		if(p.getGuoHu() == false){
			return false;
		}
		if(p.getGuoHuPai() != null && p.getGuoHuPai() == pai){
			return true;
		}
		return false;
	}
	
}
