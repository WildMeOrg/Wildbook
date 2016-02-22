scheduler.locale = {
	date:{
		month_full:["January","February","March","April","May","June","July","August","September","October","November","December"],
		month_short:["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
		day_full:["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"],
		day_short:["Sun","Mon","Tue","Wed","Thu","Fri","Sat"]
	},
	labels:{
		dhx_cal_today_button:"Today",day_tab:"Day",week_tab:"Week",month_tab:"Month",new_event:"New event",icon_save:"Save",icon_cancel:"Cancel",icon_details:"Details",icon_edit:"Edit",icon_delete:"Delete",confirm_closing:"",confirm_deleting:"Event will be deleted permanently, are you sure?",section_description:"Description",section_time:"Time period",confirm_recurring:"Do you want to edit the whole set of repeated events?",section_recurring:"Repeat event",button_recurring:"Disabled",button_recurring_open:"Enabled",agenda_tab:"Agenda",date:"Date",description:"Description",year_tab:"Year"
	}
};
scheduler.config = {
	default_date:"%j %M %Y",month_date:"%F %Y",load_date:"%Y-%m-%d",week_date:"%l",day_date:"%D, %F %j",hour_date:"%H:%i",month_day:"%d",xml_date:"%m/%d/%Y %H:%i",api_date:"%d-%m-%Y %H:%i",hour_size_px:42,time_step:5,start_on_monday:1,first_hour:0,last_hour:24,readonly:false,drag_resize:1,drag_move:1,drag_create:1,dblclick_create:1,edit_on_create:1,details_on_create:0,click_form_details:0,server_utc:false,positive_closing:false,icons_edit:["icon_save","icon_cancel"],icons_select:["icon_details","icon_edit","icon_delete"],
	lightbox:{sections:[{name:"description",height:200,map_to:"text",type:"textarea",focus:true},{name:"time",height:72,type:"time",map_to:"auto"}]	}
};
