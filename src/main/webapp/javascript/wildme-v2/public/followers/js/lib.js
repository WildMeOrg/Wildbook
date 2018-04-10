// JavaScript Document
var global_user_id = 0;

window.addEventListener('load', function (){

if (window.jQuery) 
	{
		$(document).ready(function() {	
			FindFollowers();
			addEventListener("message", receiveMessage, false);
		});
		
	} else {

	  var jq = document.createElement('script'); jq.type = 'text/javascript';
	  // Path to jquery.js file, eg. Google hosted version
	  jq.src = '//code.jquery.com/jquery-1.11.0.min.js';
	  document.getElementsByTagName('head')[0].appendChild(jq);
		
		$(document).ready(function() {	
			FindFollowers();
			addEventListener("message", receiveMessage, false);
		});
}
		
});

function FindFollowers(){
$('.list-comment-follow2').each(function(ind, val) {
		
		var animal_id 	=   $(this).data('id');
		$.get("//digital.cygnismedia.com/wildme-v2/public/api/global_follows",{animal_id:animal_id},function(data){
		
			if(data.status == 'success')
			{
				var htmls = '<link rel="stylesheet" href="//digital.cygnismedia.com/wildme-v2/public/followers/css/style.css?timer=4" type="text/css"><div class="list-comment-counts-follow2 custom2"><span class="custom5"></span> WILD ME <span class="custom4"></span></div><div class="list-comment-count-bg-follow2 custom1" id="wild_me_followers">'+data.totalfollows+'</div>';
				
				$('.list-comment-follow2').html(htmls);
				
				$('.list-comment-follow2').click(function(){
					
					$('.list-comment-follow2').append('<iframe src="//digital.cygnismedia.com/wildme-v2/public/login" width="0" height="0"></iframe>');
					//window.open('http://fb.wildme.org/wildme/public/profile/'+animal_id, '_blank');
					//return false;
				
				});
			}
		}, "json" );
	});
}

function receiveMessage(event)
{
	 if( event.data > 0){
	 global_user_id = event.data;
	 
	 var animal_id =  $('.list-comment-follow2').data('id');
	 
	 $.post("//digital.cygnismedia.com/wildme-v2/public/api/follow",{user_id:global_user_id, animal_id:animal_id,'user_type':'website'},function(data){
			
			var prevFollowCount	=	parseInt($('#wild_me_followers').text());
			
			if(data.status === 'success')
			{				
				$("#wild_me_followers").html(prevFollowCount+1)
			}

		}, "json");
  }
 
}

