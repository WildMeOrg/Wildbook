$(document).ready(function(){
	$('body').append("<textarea id='code'></textarea>");
	$("#code").hide();
	$(".ql-toolbar").append("<button style='float:right;margin-right:20px;width:auto;' id='switchCode'>SHOW HTML</button>");
	
	$(".ql-editor").on("click", "img", function (e){
		e.preventDefault();
		$('#addImgAttr').css( 'position', 'absolute' );
		$('#addImgAttr').css( 'top', e.pageY);
		$('#addImgAttr').css( 'left', e.pageX );
		$('#addImgAttr').css( 'z-index','1');
		$('#addImgAttr').show();
		
		var imgElem = $(this)
		$("#imgWidth").val('');
		$("#imgHeight").val('');
		$("#imgVspace").val('');
		$("#imgHspace").val('');
		
		$("#imgWidth").change(function(){
			var imWidth = $("#imgWidth").val();
			if(imWidth > 99)
				imgElem.attr('width',imWidth);
			else
				alert("Width Must be greater than 100")
		})
		$("#imgHeight").change(function(){
			var imgHeight = $("#imgHeight").val();
			if(imgHeight > 99)
				imgElem.attr('height',imgHeight);
			else
				alert("Height Must be greater than 100")
		})
		$("#imgVspace").change(function(){
			var imgVspace = $("#imgVspace").val();
			if(imgVspace > 1)
				imgElem.attr('vspace',imgVspace);
		})
		$("#imgHspace").change(function(){
			var imgHspace = $("#imgHspace").val();
			if(imgHspace > 1)
				imgElem.attr('hspace',imgHspace);
		})
		$("#imAlign").change(function(){
			var imAlign = $("#imAlign").val();
			if(imAlign == 'middle'){
				imgElem.attr('class','center')
			}
			else{
				imgElem.attr('align',imAlign);
				imgElem.attr('class','')
			}
			
		})
		$("#imgSetBtn").click(function(){
			$('#addImgAttr').hide();
		})
	})
	$(".ql-editor").on("click", "a", function (e){
		e.preventDefault();
		$('#addLinkAttr').css( 'position', 'absolute' );
		$('#addLinkAttr').css( 'top', e.pageY + 60);
		$('#addLinkAttr').css( 'left', e.pageX );
		$('#addLinkAttr').css( 'z-index','1');
		$('#addLinkAttr').show();
		
		var linkElem = $(this);
		$("#linkAttr").val('')
		$("#linkAttr").change(function(){
			var t = $("#linkAttr").val();
			linkElem.attr('target',t);
		})
		$("#linkSetBtn").click(function(){
			$('#addLinkAttr').hide();
		})
		
	})
	$codeEnabled = 0;
	$("#switchCode").click(function(){
		if($codeEnabled == 0){
			$codeEnabled = 1;
			var s = $(".ql-editor").html();
			$("#code").val(s);
			var d = $("#code").val();
			$(".ql-editor").text(d);
			
			$("#switchCode").text("SHOW TEXT");
		}
		else{
			$codeEnabled = 0;
			var s = $(".ql-editor").text();
			$("#code").val(s);
			var d = $("#code").val();
			$(".ql-editor").html(d);
			$("#switchCode").text("SHOW HTML");
		}
	})
	
	$('body').append("<div id='addImgAttr'>	<table><tr><td><input type='number' placeholder='width' id='imgWidth' min='100' style='width:60px;height:30px' /></td><td><input type='number' placeholder='height' id='imgHeight' style='width:60px;height:30px' /></td><td><input type='number' placeholder='hspace' id='imgHspace' style='width:60px;height:30px' /></td><td><input type='number' placeholder='vspace' id='imgVspace' style='width:60px;height:30px' /></td><td><select id='imAlign' style='width:60px;height:30px'><option value='unset'>Unset</option><option value='left'>Left</option><option value='right'>Right</option><option value='top'>Top</option><option value='bottom'>Bottom</option><option value='middle'>Middle</option></select></td><td><input type='button' value='Close' id='imgSetBtn' style='width:60px;height:30px' /></td></tr></table></div>");
	$('#addImgAttr').hide();
	$('body').append("<div id='addLinkAttr'><table><tr><td><select id='linkAttr' style='width:60px;height:30px'><option value=''>Default</option><option value='_blank'>Blank</option><option value='_self'>Self</option></select></td><td><input type='button' value='Close' id='linkSetBtn' style='width:60px;height:30px' /></td></tr></table></div>");
	$('#addLinkAttr').hide();
})