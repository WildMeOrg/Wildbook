var occFuncs = {};

occFuncs.checkIfOldFormValue = function(changedElem) {
  return ($(changedElem).attr("name").startsWith("oldValue-"));
}

occFuncs.checkIfOldFormValue$ = function($changedElem) {
  return (changedElem.attr("name").startsWith("oldValue-"));
}

occFuncs.markFormFieldNotOld = function(changedElem) {
  if (occFuncs.checkIfOldFormValue(changedElem)) {
    $(changedElem).attr("name",$(changedElem).attr("name").substring(9));
    $(changedElem).closest('tr').addClass("changed-row");
  }
}

occFuncs.markFormFieldOld$ = function($inputElem) {
  if (!occFuncs.checkIfOldFormValue($inputElem)){
    $inputElem.attr("name","oldValue-"+$inputElem.attr("name"));
  }
}

$(document).ready(function() {

  $(function () {
    var dateNow = new Date();
    $('#datetimepicker').datetimepicker({
        //defaultDate:dateNow
    });
  });

  var changedFields = {};

  $("td.undo-container div.undo-button").click(function(event) {
    event.stopPropagation();
    var changedRow = $(this).closest('tr.changed-row');
    changedRow.removeClass('changed-row');
    var original = changedRow.attr('data-original-value');
    var correspondingInput = changedRow.find('td input');
    correspondingInput.val(original);
    occFuncs.markFormFieldOld$(correspondingInput);
  });

	$('#occform input,#occform select').change(function() {
    occFuncs.markFormFieldNotOld(this);
    var str = $(this).val();
    console.log('Change handler called on elem' + $(this).attr("name") + " to new val " + str);
    changedFields[$(this).attr("name")] = str;
		$('.submit').addClass('changes-made');
		$('.submit .note').html('changes made. please save.');
    console.log('Change detected on form. ChangedFields = '+JSON.stringify(changedFields));
	});
});
