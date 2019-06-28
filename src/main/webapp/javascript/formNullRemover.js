/* the below function removes any blank-valued params from the form just before submitting, making the searchResults.jsp url MUCH cleaner and more readable. h/t https://stackoverflow.com/questions/8029532/how-to-prevent-submitting-the-html-forms-input-field-value-if-it-empty */
$('#search').submit(function() {
  $(this)
    .find('input[name]')
    .filter(function () {
        return !this.value;
    })
    .prop('name', '');
	console.log('');;
	console.log('===============');;
  console.log("formNullRemover was called on elem id "+$(this).attr('id')+" and name "+$(this).attr('name'));
 	console.log('');;

 });