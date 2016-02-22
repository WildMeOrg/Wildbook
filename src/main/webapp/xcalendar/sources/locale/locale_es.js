scheduler.locale = {
	date:{
		month_full:["Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"],
		month_short:["Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"],
		day_full:["Domingo","Lunes","Martes","Miércoles","Jueves","Viernes","Sábado"],
		day_short:["Dom","Lun","Mar","Mié","Jue","Vie","Sab"]
	},
	labels:{
		dhx_cal_today_button:"Hoy",day_tab:"Día",week_tab:"Semana",month_tab:"Mes",new_event:"Nuevo evento",icon_save:"Guardar",icon_cancel:"Cancelar",icon_details:"Detalles",icon_edit:"Editar",icon_delete:"Borrar",confirm_closing:"",confirm_deleting:"Evento se eliminará de forma permanente, ¿está seguro?",section_description:"Descripción",section_time:"Período de tiempo",confirm_recurring:"¿Quieres editar el conjunto de eventos repetidos?",section_recurring:"Evento del repetición",button_recurring:"Deshabilitado",button_recurring_open:"Habilitado",agenda_tab:"Agenda",date:"Fecha",description:"Descripción",year_tab:"Año"
	}
};
scheduler.config = {
	default_date:"%j %M %Y",month_date:"%F %Y",load_date:"%Y-%m-%d",week_date:"%l",day_date:"%D, %F %j",hour_date:"%H:%i",month_day:"%d",xml_date:"%m/%d/%Y %H:%i",api_date:"%d-%m-%Y %H:%i",hour_size_px:42,time_step:5,start_on_monday:1,first_hour:0,last_hour:24,readonly:false,drag_resize:1,drag_move:1,drag_create:1,dblclick_create:1,edit_on_create:1,details_on_create:0,click_form_details:0,server_utc:false,positive_closing:false,icons_edit:["icon_save","icon_cancel"],icons_select:["icon_details","icon_edit","icon_delete"],
	lightbox:{sections:[{name:"description",height:200,map_to:"text",type:"textarea",focus:true},{name:"time",height:72,type:"time",map_to:"auto"}]}
};
