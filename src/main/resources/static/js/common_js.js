//공통처리
$(function(){
	//URL 조정(뒤에 / 붙지 않게 처리)
	var URLCheck = location.href;
	if(URLCheck.lastIndexOf("/")==URLCheck.length-1){
		URLCheck = URLCheck.substring(0,URLCheck.lastIndexOf("/"));
		history.pushState(null,null,URLCheck);
	}
	
	
});