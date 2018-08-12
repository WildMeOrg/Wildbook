var classEditTemplate = {};

classEditTemplate.checkIfOldFormValue = function(changedElem) {
  return ($(changedElem).attr("name").startsWith("oldValue-"));
}

classEditTemplate.checkIfOldFormValue$ = function($changedElem) {
  return (changedElem.attr("name").startsWith("oldValue-"));
}

classEditTemplate.markFormFieldNotOld = function(changedElem) {
  if (classEditTemplate.checkIfOldFormValue(changedElem)) {
    $(changedElem).attr("name",$(changedElem).attr("name").substring(9));
    $(changedElem).closest('tr').addClass("changed-row");
  }
}

classEditTemplate.markFormFieldOld$ = function($inputElem) {
  if (!classEditTemplate.checkIfOldFormValue($inputElem)){
    $inputElem.attr("name","oldValue-"+$inputElem.attr("name"));
  }
}

$(document).ready(function() {

  var changedFields = {};

  $("td.undo-container div.undo-button").click(function(event) {
    event.stopPropagation();
    var changedRow = $(this).closest('tr.changed-row');
    changedRow.removeClass('changed-row');
    var original = changedRow.attr('data-original-value');
    var correspondingInput = changedRow.find('td input');
    correspondingInput.val(original);
    classEditTemplate.markFormFieldOld$(correspondingInput);
  });

	$('#classEditTemplateForm input, #classEditTemplateForm select').change(function() {
    console.log("classEdit")
    classEditTemplate.markFormFieldNotOld(this);
    var str = $(this).val();
    console.log('Change handler called on elem' + $(this).attr("name") + " to new val " + str);
    changedFields[$(this).attr("name")] = str;
		$('.submit').addClass('changes-made');
		$('.submit .note').html('changes made. please save.');
    console.log('Change detected on form. ChangedFields = '+JSON.stringify(changedFields));
	});

  $( ".datepicker" ).datetimepicker({
    changeMonth: true,
    changeYear: true,
    dateFormat: 'yy-mm-dd',
    maxDate: '+1d',
    controlType: 'select',
    alwaysSetTime: false
  });
  $( ".datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );




});
