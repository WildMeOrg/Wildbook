
wildbook.NoteField = {
    originalContent: {},
    quill: {},

    editClick: function(wrap, ev) {
        var id = wrap.id.substring(5);
        if (wildbook.NoteField.quill[id]) return;
        wildbook.NoteField.originalContent[id] = document.getElementById('id-' + id).innerHTML;
        $(wrap).find('.org-ecocean-notefield-control').css('visibility', 'hidden');

        /// see:  https://github.com/quilljs/quill/issues/1139
        var Link = Quill.import('formats/link');
        class MyLink extends Link {
            static create(value) {
                let node = super.create(value);
                value = this.sanitize(value);
                node.setAttribute('href', value);
                node.removeAttribute('target');
                return node;
            }
        }
        Quill.register(MyLink);

        wildbook.NoteField.quill[id] = new Quill('#id-' + id, {
            modules: {
                toolbar: [
                    [{ 'header': [1, 2, 3, 4, 5, 6, false] }],
                    ['bold', 'italic', 'underline', 'strike'],
                    ['image', 'blockquote'],
	            ['link','table'],
	            [{ 'list': 'ordered'}, { 'list': 'bullet' }],
	            [{ 'script': 'sub'}, { 'script': 'super' }],      // superscript/subscript
	            [{ 'indent': '-1'}, { 'indent': '+1' }],          // outdent/indent
	            [{ 'direction': 'rtl' }],                         // text direction

	            [{ 'color': [] }, { 'background': [] }],          // dropdown with defaults from theme
	            [{ 'font': [] }],
	            [{ 'align': [] }],

	            ['clean']      
                ]
            },
            theme: 'snow'
        });
        var menus = '<span class="ql-formats">';
        menus += '<button class="wb-ql-menu el el-ok" title="save" onClick="return wildbook.NoteField.save(this);" type="button"></button>';
        menus += '<button class="wb-ql-menu el el-remove" title="cancel" onClick="return wildbook.NoteField.cancel(this);" type="button"></button>';
        menus += '</span>';
        $(wildbook.NoteField.quill[id].getModule('toolbar').container).append(menus);
        wildbook.NoteField.kitsciExtend();
    },

    closeEdit: function(id, content) {  //content overrides quill content
        if (!content) content = wildbook.NoteField.quill[id].root.innerHTML;
        $('#wrap-' + id + ' .ql-toolbar').remove();
        $('#id-' + id).removeClass('ql-container').removeClass('ql-snow').html(content);
        delete(wildbook.NoteField.quill[id]);
        return content;
    },

    save: function(el) {
        var id = el.parentElement.parentElement.parentElement.id.substring(5);
        var content = wildbook.NoteField.closeEdit(id);
        $.ajax({
            url: wildbookGlobals.baseUrl + '/NoteFieldEdit',
            type: 'POST',
            data: JSON.stringify({id: id, content: content}),
            contentType: 'application/javascript',
            complete: function(x) {
                console.info('NoteField save id=%s: x=%o', id, x);
                if (!x || !x.responseJSON) {
                    alert('unable to save changes');
                } else if (!x.responseJSON.success) {
                    alert('unable to save changes: ' + x.responseJSON.error);
                } else {
                    //successful!
                }
            },
            dataType: 'json'
        });
        $('#wrap-' + id).find('.org-ecocean-notefield-control').css('visibility', 'visible');
    },

    cancel: function(el) {
        var id = el.parentElement.parentElement.parentElement.id.substring(5);
        wildbook.NoteField.closeEdit(id, wildbook.NoteField.originalContent[id]);
        $('#wrap-' + id).find('.org-ecocean-notefield-control').css('visibility', 'visible');
    },

    tryDefault: function(el) {
        var id = el.id.substring(3);
        var def = document.getElementById('default-' + id);
        if (def) el.innerHTML = def.innerHTML;
    }, 

    initDiv: function(el) {
        var id = el.id.substring(3);
        var w = $('<div class="org-ecocean-notefield-wrapper" id="wrap-' + id + '" />');
        var def = document.getElementById('default-' + id);
        if ((el.innerHTML == '') && def) el.innerHTML = def.innerHTML;
        $(el).wrap(w)
        $(el).before('<div class="org-ecocean-notefield-control el el-edit" />');
    },

    kitsciExtend: function() {
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
		$("#imAlign").val('');
		$imgid = this.id = "img_" + Math.round(e.timeStamp)
		var elem = this;
		
		$("#imgWidth").change(function(){
			var imWidth = $("#imgWidth").val();
			if(imWidth > 99)
				$("#" + $imgid).attr('width',imWidth);
			else
				alert("Width Must be greater than 100")
		})
		$("#imgHeight").change(function(){
			var imgHeight = $("#imgHeight").val();
			if(imgHeight > 99)
				$("#" + $imgid).attr('height',imgHeight);
			else
				alert("Height Must be greater than 100")
		})
		$("#imgVspace").change(function(){
			var imgVspace = $("#imgVspace").val();
			if(imgVspace > 1)
				$("#" + $imgid).attr('vspace',imgVspace);
		})
		$("#imgHspace").change(function(){
			var imgHspace = $("#imgHspace").val();
			if(imgHspace > 1)
				$("#" + $imgid).attr('hspace',imgHspace);
		})
		$("#imAlign").change(function(){
			var imAlign = $("#imAlign").val();
			$("#" + $imgid).attr('align', imAlign);
		})
		$("#imgSetBtn").click(function(){
			$('#addImgAttr').hide();
		})
	})
	$(".ql-editor").on("click","a", function (e){
		e.preventDefault();
		$('#addLinkAttr').css( 'position', 'absolute' );
		$('#addLinkAttr').css( 'top', e.pageY + 60);
		$('#addLinkAttr').css( 'left', e.pageX );
		$('#addLinkAttr').css( 'z-index','1');
		$('#addLinkAttr').show();
		$id = this.id = "link_" + Math.round(e.timeStamp)
		var linkElem =this;
		
		$("#linkAttr").val('')
		$("#linkAttr").bind('click',function(e){
			var t = $("#linkAttr").val();
			$("#" + $id).attr('target',t)
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

	$('body').append("<div id='addImgAttr'>	<table><tr><td><input type='number' placeholder='width' id='imgWidth' min='100' style='width:60px;height:30px' /></td><td><input type='number' placeholder='height' id='imgHeight' style='width:60px;height:30px' /></td></tr><tr><td><input type='number' placeholder='hspace' id='imgHspace' style='width:60px;height:30px' /></td><td><input type='number' placeholder='vspace' id='imgVspace' style='width:60px;height:30px' /></td></tr><tr><td><select id='imAlign' style='width:60px;height:30px'><option value='unset'>Unset</option><option value='left'>Left</option><option value='right'>Right</option><option value='top'>Top</option><option value='bottom'>Bottom</option><option value='middle'>Middle</option></select></td><td><input type='button' value='Close' id='imgSetBtn' style='width:60px;height:30px' /></td></tr></table></div>");
	$('#addImgAttr').hide();
	$('body').append("<div id='addLinkAttr'><table><tr><td><select id='linkAttr' style='width:60px;height:30px'><option value=''>Default</option><option value='_blank'>Blank</option><option value='_self'>Self</option></select></td><td><input type='button' value='Close' id='linkSetBtn' style='width:60px;height:30px' /></td></tr></table></div>");
	$('#addLinkAttr').hide();
    }

};


$('body').ready(function() {
    $('.org-ecocean-notefield').each(function(i, el) {
        wildbook.NoteField.initDiv(el);
    });
    $('.org-ecocean-notefield-need-default').each(function(i, el) {
        wildbook.NoteField.tryDefault(el);
    });
    $('.org-ecocean-notefield-control').on('mouseenter mouseleave', function(ev) {
        if (ev.type == 'mouseenter') {
            ev.target.parentElement.classList.add('org-ecocean-notefield-outlined');
        } else {
            ev.target.parentElement.classList.remove('org-ecocean-notefield-outlined');
        }
    });
    $('.org-ecocean-notefield-control').on('click', function(ev) {
        wildbook.NoteField.editClick(ev.target.parentElement, ev);
    });
/*
  var quill = new Quill('#editor', {
    theme: 'snow'
  });
*/
});
