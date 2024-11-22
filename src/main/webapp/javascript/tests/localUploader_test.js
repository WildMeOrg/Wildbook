QUnit.module('filesChangedSetFilename', () => {
    QUnit.test('sets original and hidden filename', assert => {
        let f = {files: [{name: 'my_cool_test.xlsx'}]};
        filesChangedSetFilename(f);
        assert.equal(document.getElementById("originalFilename").innerHTML, 'my_cool_test.xlsx');
        assert.equal(document.getElementById("hiddenFilename").innerHTML, 'mycooltest.xlsx');
    });

    QUnit.test('handles filename with spaces', assert => {
        let f = {files: [{name: 'my cool test.txt'}]};
        filesChangedSetFilename(f);
        assert.equal(document.getElementById("originalFilename").innerHTML, 'my cool test.txt');
        assert.equal(document.getElementById("hiddenFilename").innerHTML, 'mycooltest.txt');
    });

    QUnit.test('handles filename with multiple periods', assert => {
        let f = {files: [{name: 'my_cool_test.file.xlsx'}]};
        filesChangedSetFilename(f);
        assert.equal(document.getElementById("originalFilename").innerHTML, 'my_cool_test.file.xlsx');
        assert.equal(document.getElementById("hiddenFilename").innerHTML, 'mycooltestfile.xlsx');
    });
});
