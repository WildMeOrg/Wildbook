
wildbook.NoteField = {
    className: 'org-ecocean-notefield',
    originalContent: {},
    quill: {},

    editClick: function(wrap, ev) {
        var id = wrap.id.substring(5);
        if (wildbook.NoteField.quill[id]) return;
        wildbook.NoteField.originalContent[id] = document.getElementById(id).innerHTML;
        $(wrap).find('.org-ecocean-notefield-control').css('visibility', 'hidden');
        wildbook.NoteField.quill[id] = new Quill('#' + id, {
            theme: 'snow'
        });
        var menus = '<span class="ql-formats">';
        menus += '<button class="wb-ql-menu el el-ok" title="save" onClick="return wildbook.NoteField.save(this);" type="button"></button>';
        menus += '<button class="wb-ql-menu el el-remove" title="cancel" onClick="return wildbook.NoteField.cancel(this);" type="button"></button>';
        menus += '</span>';
        $(wildbook.NoteField.quill[id].getModule('toolbar').container).append(menus);
    },

    closeEdit: function(id, contents) {  //contents overrides quill contents
        if (!contents) contents = wildbook.NoteField.quill[id].root.innerHTML;
        $('#wrap-' + id + ' .ql-toolbar').remove();
        $('#' + id).removeClass('ql-container').removeClass('ql-snow').html(contents);
        delete(wildbook.NoteField.quill[id]);
    },

    save: function(el) {
        var id = el.parentElement.parentElement.parentElement.id.substring(5);
        wildbook.NoteField.closeEdit(id);
        $('#wrap-' + id).find('.org-ecocean-notefield-control').css('visibility', 'visible');
    },

    cancel: function(el) {
        var id = el.parentElement.parentElement.parentElement.id.substring(5);
        wildbook.NoteField.closeEdit(id, wildbook.NoteField.originalContent[id]);
        $('#wrap-' + id).find('.org-ecocean-notefield-control').css('visibility', 'visible');
    },

    initDiv: function(el) {
        var w = $('<div class="org-ecocean-notefield-wrapper" id="wrap-' + el.id + '" />');
        $(el).wrap(w)
        $(el).before('<div class="org-ecocean-notefield-control el el-edit" />');
    }
};


$('body').ready(function() {
    $('.' + wildbook.NoteField.className).each(function(i, el) {
        wildbook.NoteField.initDiv(el);
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
