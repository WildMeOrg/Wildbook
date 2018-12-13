
beforeEach(()=>{
  cy.visit('/');
  cy.on('uncaught:exception', (err, runnable) => {
  expect(err.message).to.include('of undefined')
    done()
    return false
  });
});

describe('Wildbook instance landing page', function() {
  it('visits landing page and finds something that says submit!', function() {
    cy.contains('Submit');
  });
});

describe('Wildbook instance landing page known bugs', function() {
  it('looks for text containing null', function() {
    cy.contains('Mark Aaron Fisher').should('not.exist');
    cy.contains('null').should('not.exist');
  });
});
