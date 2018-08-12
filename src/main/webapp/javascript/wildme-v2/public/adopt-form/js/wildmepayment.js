var global_user_idss = 0;
var global_category_url = '';
var global_stage = 1;
window.addEventListener('load', function (){

if (window.jQuery) 
	{
		PutButton();
		$.isValidEmail = function (em_address) 
		{
			var email=/^[A-Za-z0-9]+([_\.-][A-Za-z0-9]+)*@[A-Za-z0-9]+([_\.-][A-Za-z0-9]+)*\.([A-Za-z]){2,4}$/i;
			return (email.test(em_address))
		} //
		
	} else {

	  var jq = document.createElement('script'); jq.type = 'text/javascript';
	  // Path to jquery.js file, eg. Google hosted version
	  jq.src = '//code.jquery.com/jquery-1.11.0.min.js';
	  document.getElementsByTagName('head')[0].appendChild(jq);
		
	$(document).ready(function(e) {
	PutButton();
	
		$.isValidEmail = function (em_address) 
		{
			var email=/^[A-Za-z0-9]+([_\.-][A-Za-z0-9]+)*@[A-Za-z0-9]+([_\.-][A-Za-z0-9]+)*\.([A-Za-z]){2,4}$/i;
			return (email.test(em_address))
		} //
	});
	
	}
		

});

var current_server = location.protocol+"//digital.cygnismedia.com/wildme-v2/public/";
var global_uid = 0;
var wildme_category_id=0;
var global_price = 0;

function PutButton(){
	
	$("#wildme-adoption-button").html('<link rel="stylesheet" href="//digital.cygnismedia.com/wildme-v2/public/adopt-form/css/style.css?timer=4" type="text/css"><div id="adoption_button" class="adopt-button"> <a href="javascript:;"><i></i> Adopt </a></div>');

	$("#wildme-adoption-button").click(function(e) {
		WildMegetAnimalDetails();
    });	
	
}

//setTimeout(function(){}, 2000);

function WildMegetAnimalDetails(){
	
		var animal_id =  $('#wildme-adoption-button').data('id');
		$.ajax({
				type: 'GET',
				dataType : 'json',
				url:  current_server+"api/animal_detail",
				data: {'animal_id':animal_id},
				beforeSend:function(){},
				success:function(data){
					
						if(data.status == 'success')
						{
							$('#wildme-adoption-button').attr('data-animal_id',data.records[0].wildme_animal_id);
							 DisplayPaymentPopup(data.records);
						}else{
							
							$("#wildme-adoption-form").append('<link rel="stylesheet" href="//digital.cygnismedia.com/wildme-v2/public/adopt-form/css/adoption.css?timer=4" type="text/css"><div id="wildme_adopt-form" style="" class="popup"><div class="adopt-form-wrapper" style="min-height: 168px;"><span class="cross-icon-adopt"><img src="//digital.cygnismedia.com/wildme-v2/public/adopt-form/images/cross-icon.png" width="22" height="22" alt="" /></span> <div class="adopt-form-title"><div class="adopt-form-logo"><img src="//digital.cygnismedia.com/wildme-v2/public/images/logo.png" width="158" height="53" alt="" /></div></div><div style="" id="adopt-step4" class="adopt-slide"><div class="adopt-slide-inner"><p class="adopt-thank-you" style="text-align: center; ">Error While Adoption procedure, Please try again later</p><span id="close-adopt" class="close-text">Close</span></div></div></div>');	
							InitializeJs();
						}
					},
					error:function(err){
						
					}
				});
}
function DisplayPaymentPopup(data){
 animal_class = 'blue';
  var disabled_name = '';
  var disabled_qoute = '';
  var id = data[0].wildme_animal_id;
  var label = data[0].wildme_animal_label;
  var cat = data[0].wildme_category_name;
  global_price = data[0].wildme_animal_price;
  var icon = current_server+data[0].wildme_icon;  
  var nickname = data[0].wildme_animal_nick;
  var quote = data[0].wildme_animal_quote;
  wildme_category_id = data[0].wildme_category_id;
 

  if(wildme_category_id == 2){
	  animal_class = 'orange';
  }
  if(wildme_category_id == 3){
	  animal_class = 'yellow';
  }

  var user_type = $('#wildme-adoption-button').data('user_type');
  var app_url = window.location+ window.location.pathname; //$('#wildme-aboption-div').data('app_url');
 	
	if(typeof user_type == 'undefined' || user_type != 'application'){
		user_type = 'website';
	}
	if(nickname == null || nickname == 0 || nickname == '') nickname = '';
	else nickname = nickname+' ';
var popupHtml = '<link rel="stylesheet" href="//digital.cygnismedia.com/wildme-v2/public/adopt-form/css/adoption.css?timer=4" type="text/css"><div class="popup" style="display:none;" id="wildme_adopt-form">\
  <div class="adopt-form-wrapper">\
  <span class="cross-icon-adopt"><img src="//digital.cygnismedia.com/wildme-v2/public/adopt-form/images/cross-icon.png" width="22" height="22" alt="" /></span>\
    <div class="adopt-form-title">\
      <div class="adopt-form-logo"><img src="//digital.cygnismedia.com/wildme-v2/public/images/logo.png" width="158" height="53" alt="" /></div>\
    </div>\
    <div class="adopt-error-div"> Invalid card number. Please enter correct card number </div>\
    <div class="animal-code-strip">\
      <div class="animal-thumb align-div '+animal_class+'"><img src="'+icon+'" width="39" height="39"  alt=""/></div>\
      <h2 id="animal_">'+nickname+'('+label+')<br />\
        <span>Type: '+cat+' </span> </h2>\
      <div class="animal-price-box"  > <span>Price: $'+global_price+'</span> </div>\
    </div>\
    <hr class="hori-line" />\
    <div class="adopt-slide" id="adopt-step1" >\
      <h3>Your adoption has the following benefits</h3>\
      <ul>\
        <li>Earns you a Champion Badge for your WildMe Profile.</li>\
        <li>Adoptions are valid for one year.</li>\
        <li>The recent activities of the animal that you adopted will be displayed\
          on your Facebook wall.</li>\
        <li>You are able to adopt multiple animal profiles.</li>\
        <li> Your adoption is a donation to Wild Me, a 501(c)(3) non-profit organization.</li>\
        <li>Your adoption is tax deductible in the United States.</li>\
        <li>The largest share of the proceeds will go to the research organization studying this animal.</li>\
      </ul>\
      <div id="adopt-btn1" class="adopt-outline-btn"><span class="adopti-spinner rotating"></span><a href="javascript:;">get started</a></div>\
    </div>\
    <div class="adopt-slide" id="adopt-step2" style="display:none;">\
      <div class="adopt-slide-inner"><div id="success-msg3" style="display:none;" class="adopt-successful-div">The payment process has successfully completed !</div><div id="error-msg" style="display:none;" class="adopt-error-div">Error : Please enter a valid credit card number.</div>\
        <div class="adopt-field cc_name">\
          <input type="text" id="wildme_cc_name" placeholder="Card Holder Name"/>\
        </div>\
		 <div class="adopt-field cc_email">\
          <input type="text" id="wildme_cc_email" placeholder="Card Holder Email"/>\
        </div>\
        <div class="adopt-field cc_num card-field">\
          <input type="text" id="wildme_cc_num" placeholder="Card Number"/>\
        </div>\
	<div class="adopt-field select half-field last wildme_cc_type" >\
<select  id="wildme_cc_type">\
<option value="0">Card Type</option>\
<option value="Visa">Visa</option>\
<option value="MasterCard">Master Card</option>\
<option value="Discover">Discover</option>\
<option value="Amex">Amex</option>\
</select>\
        </div>\
        <div class="adopt-field half-field cc_month">\
          <input type="text" id="wildme_cc_month" placeholder="Expiry Month"/>\
        </div>\
        <div class="adopt-field half-field cc_year">\
          <input type="text" id="wildme_cc_year" placeholder="Expiry Year"/>\
        </div>\
        <div class="adopt-field half-field last cc_ccv">\
        <div class="cvv-notificaiton">3 digit CCV code</div>\
         <i class="ques-icon"></i>\
          <input type="text" id="wildme_cc_ccv" placeholder="Enter CCV"/>\
        </div>\
        <br clear="all" />\
      </div>\
      <div id="adopt-btn2" class="adopt-outline-btn margin-top"> <span id="wildme_payment_spinner" class="adopti-spinner rotating"></span> <a id="wildme_do_transaction" href="javascript:;">Continue</a> </div>\
    </div>\
    <div class="adopt-slide" id="adopt-step3" style="display:none;">\
      <div class="adopt-slide-inner">\
        <div class="adopt-successful-div" id="success-msg">The payment process has successfully completed !</div>\
        <p class="simple-text">Congratulations! You have the opportunity to give a unique nickname to this animal. Pick a good one for us!</p>';
		
		if(nickname != 0 && nickname != null){
			
			disabled_name = 'disabled="disabled" ';
		}else{
			nickname = '';
		}
		
		popupHtml += '<div class="adopt-field nick">\
        <input '+disabled_name+' type="text" id="animal_nick" placeholder="Enter nick name" value="'+nickname+'" />\
        </div>';
		
		if(quote != 0 && quote != null){
			disabled_qoute  = 'disabled="disabled" ';
		}else{
			quote = '';
		}
		
		popupHtml += '<div class="adopt-field quote">\
        <input '+disabled_qoute+' type="text" id="animal_quote" placeholder="Enter quote  (Maximum 70 characters)"  maxlength="70" value="'+quote+'" />\
		</div>';
				
      popupHtml += '</div>\
      <div id="adopt-btn3" class="adopt-outline-btn margin-top"> <span class="adopti-spinner rotating" id="spinner-submit"></span> <a id="wildme_submit" href="javascript:;">Submit</a> </div>\
    </div>\
    <div class="adopt-slide" id="adopt-step4" style="display:none;">\
      <div class="adopt-slide-inner">\
        <p class="adopt-thank-you">Thank you for adopting '+nickname+label+'. You have been assigned a Champion badge for your WildMe profile. Share the news with your friends and ask them to earn their Champion badge as well.</p>\
        <div class="batch"><img src="//digital.cygnismedia.com/wildme-v2/public/images/batch.png" width="116" height="116" /></div>\
      </div>\
      <div id="adopt-btn4" class="adopt-outline-btn margin-top "> <span class="adopti-spinner rotating"></span> <a href="javascript:;" class="share-btn"><i></i>Share</a> </div>\
      <span class="close-text" id="close-adopt">Close</span>\
    </div>\
  </div>\
</div>';
	
	$("#wildme-adoption-form").html(popupHtml);
	
	InitializeJs();

}

function InitializeJs(){
	
 $("#wildme_adopt-form").fadeIn('slow');
//adopt form
$(".adopt-button").click(function(){
	 $("#adopt-form").show("fade", 600);
});
$(".cross-icon-adopt, .close-text").click(function(){
	 
	 $("#wildme_adopt-form").fadeOut(function(){
		 $("#wildme_adopt-form").remove();
		 $("#wildme-adoption-form").html('');
	});
	
	if(global_stage == 4){
		window.top.location=global_category_url;
	}
	document.styleSheets.reload();
});

//Step 1
$("#adopt-btn1").click(function(){
	
  $("#adopt-step1").fadeOut(function(){$("#adopt-step2").fadeIn()});
});

//Step 2
$("#adopt-btn2").click(function(){

  var wildme_cc_name     = $("#wildme_cc_name").val();
  var wildme_cc_num      = $("#wildme_cc_num").val();
  var wildme_cc_month    = $("#wildme_cc_month").val();
  var wildme_cc_year     = $("#wildme_cc_year").val();
  var wildme_cc_ccv      = $("#wildme_cc_ccv").val();
  var wildme_cc_type	 = $("#wildme_cc_type").val();
  var wildme_cc_email 	 = $("#wildme_cc_email").val();
  
  var wildme_amount 	 = global_price;
  $(".cc_name").css("border-color","");
  $(".cc_num").css("border-color","");
  $(".cc_month").css("border-color","");
  $(".cc_year").css("border-color","");
  $(".cc_ccv").css("border-color","");
  $(".cc_email").css("border-color","");
  $(".wildme_cc_type").css("border-color","");
  
  if (wildme_cc_name=='')
   {
      $('#wildme_payment_spinner').hide();
      $("#wildme_cc_name").focus();
      $(".cc_name").css("border-color","red");
      return false;
   }
   else  if (wildme_cc_email==''  ||  $.isValidEmail(wildme_cc_email) != true )
   {
      $('#wildme_payment_spinner').hide();
      $("#wildme_cc_email").focus();
      $(".cc_email").css("border-color","red");
      return false;
   }
    else if (wildme_cc_num=='')
   {
     $('#wildme_payment_spinner').hide();
      $("#wildme_cc_num").focus();
      $(".cc_num").css("border-color","red");
      return false;
   }
    else if (wildme_cc_type=='0' || wildme_cc_type=='Card Type')
   {
     $('#wildme_payment_spinner').hide();
      $("#wildme_cc_type").focus();
      $(".wildme_cc_type").css("border-color","red");
      return false;
   }
   
   else if (wildme_cc_month=='')
   {
      $('#wildme_payment_spinner').hide();
      $("#wildme_cc_month").focus();
      $(".cc_month").css("border-color","red");
      return false;
   }
   else if (wildme_cc_year=='')
   {
      $('#wildme_payment_spinner').hide();
      $("#wildme_cc_year").focus();
      $(".cc_year").css("border-color","red");
      return false;
   }

   else if (wildme_cc_ccv=='' || wildme_cc_ccv.length != 3)
   {	 $('#wildme_payment_spinner').hide();

      $("#wildme_cc_ccv").focus();
      $(".cc_ccv").css("border-color","red");
      return false;
   }
   else
   {
	 $('#wildme_payment_spinner').show();
	 $('#wildme_do_transaction').text('');
	wildme_paypal_pro(wildme_cc_type, wildme_cc_num, wildme_cc_name, wildme_cc_month,wildme_cc_year, wildme_cc_ccv, '15th south avenue', '', 'New York', 'NY', 'USA', 10002, wildme_amount);

    //$('#wildme-aboption-div a').text('Continue');	
   	/* $("#adopt-step2").fadeOut(function(){$("#adopt-step3").fadeIn()});*/
   }

  ;
});

//Step 3
$("#adopt-btn3").click(function(){

  //$('#spinner-submit').show();
  var animal_nick   = '';
  var animal_quote  = '';
  var category_id   = wildme_category_id;
  var id			= $('#wildme-adoption-button').data('animal_id');
  var price			= global_price;

/*  $(".nick").css("border-color","");
  $(".quote").css("border-color","");

  if (animal_nick=='')
  {
      $('#spinner-submit').hide();
      $("#animal_nick").focus();
      $(".nick").css("border-color","red");
      return false;
  }
  else if (animal_quote=='')
  {
      $('#spinner-submit').hide();
      $("#animal_quote").focus();
      $(".quote").css("border-color","red");
      return false;
  	} else {
	  
    $('#spinner-submit').show();
    $('#wildme_submit').text('');
	*/
	var user_type = 'website';
	var app_url = window.location+ window.location.pathname; //$('#wildme-aboption-div').data('app_url');
	var wildme_cc_email 	 = $("#wildme_cc_email").val();
	/*if(typeof user_type == 'undefined' || user_type != 'application'){
		user_type = 'website';
	}*/
	var adopter_name = $("#wildme_cc_name").val();
    $.post(current_server+"api/outside_adoptor",  {uid: 0, animal_id: id, category_id: category_id,  nick_name:  animal_nick, quote: animal_quote,amount:price,status:'Active', user_type: user_type, name: adopter_name,email:wildme_cc_email}, function(data){
    $('#spinner-submit').hide();
	
      if(data.status == 'success')
      {
		    var wildme_cc_name     = $("#wildme_cc_name").val();
 			 var wildme_cc_email 	 = $("#wildme_cc_email").val();
  
		 global_category_url = data.cat_url+'AdoptionCompletion?animalID='+id+'&nickname=&quote=&adopter_name='+wildme_cc_name+'&email='+wildme_cc_email;
		 
		/*$('#wildme_submit').text('');
    cat_url
        $('.adopt-successful-div').fadeIn('fast').delay(1000).fadeOut(400, function(){ 
        $("#adopt-step3").fadeOut(function(){$("#adopt-step4").fadeIn()});
     	});*/

      }else{
	  	// $('#wildme_submit').text('Continue');
	  }
	  }, "json");
    
  // }
});

//Step 3
$(".ques-icon").hover(function(){
  $(".cvv-notificaiton").toggleClass('active');
});

//step 4
$("#adopt-btn4").click(function(e) {

 // shareAdoption(id,cat,price,image_path,nickname,app_url);
 shareAdoption();
    
});

}

function wildme_paypal_pro(cc_type, cc_number, cc_name, cc_exp_month,cc_exp_year, cc_ccv, address, phone_number, city, state, country, zipcode, amt_doller)
{
	$(".cross-icon-adopt").hide();
	$.ajax({
				type: 'POST',
				dataType : 'json',
				url:  current_server+"api/checkout",
				data: {'cc_type':cc_type,'cc_num':cc_number,'fname':cc_name,'exp_date':cc_exp_month,'exp_date2':cc_exp_year,'ccv2':cc_ccv,'address':address,'phone':phone_number,'city':city,'state':state,'country':country,'zip':zipcode,'amt':amt_doller,'desc':'WildMe Adoption Payment'},
				beforeSend:function(){},
				success:function(data){
					
					$('#wildme_payment_spinner').hide();
					//console.log(data.ACK);
						if(data.ACK == 'Success')
						{
							
							$("#adopt-step2").fadeOut(function(){
								
								$("#adopt-step4").fadeIn();
								global_stage = 4;
								$(".cross-icon-adopt").show();
								$("#adopt-btn3").trigger('click');
								
									$(".cross-icon-adopt, .close-text").click(function(){	 
										$("#wildme_adopt-form").fadeOut(function(){
										$("#wildme_adopt-form").remove();
										
										});
									});
								
								});
						}
						else if(data.L_SHORTMESSAGE0 == 'Internal Error'){
							$('#wildme_do_transaction').text('Continue');
							$("#error-msg").html('This transaction could not be proceed, please try again later').fadeIn('fast').delay(3000).fadeOut(700);	
						}
						else
						{
							$('#wildme_do_transaction').text('Continue');
							$("#error-msg").html('Invalid card number. Please enter correct card number').fadeIn('fast').delay(3000).fadeOut(700);	
						}
					},
					error:function(err){
						
					}
				});
}

function shareAdoption()
{
  var animal_id =  $('#wildme-adoption-button').data('animal_id');

  url = '//digital.cygnismedia.com/wildme-v2/public/share/'+animal_id;

  window.open('http://www.facebook.com/sharer/sharer.php?s=100&p[images][0]=&p[url]='+encodeURIComponent(url)+'&p[title]=&p[summary]=','sharer','toolbar=0,status=0,width=626,height=250');

  $(".cross-icon-adopt").trigger('click');

	window.top.location=global_category_url;
	document.styleSheets.reload();
}