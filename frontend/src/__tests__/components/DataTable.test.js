// import React from 'react';
// import { screen } from '@testing-library/react';
// import MyDataTable from '../../components/DataTable';
// import { renderWithProviders } from "../../utils/utils";

// jest.mock('react-intl', () => ({
//   FormattedMessage: ({ id }) => <span data-testid={`intl-${id}`}>{id}</span>,
//   useIntl: () => ({
//     formatMessage: ({ id }) => id,
//   }),
// }));

// jest.mock('react-data-table-component', () => (props) => (
//   <div data-testid="mock-table">
//     {props.data.map((row, i) => (
//       <div key={i} data-testid="row" onClick={() => props.onRowClicked?.(row)}>
//         Row-{i}
//       </div>
//     ))}
//   </div>
// ));
// jest.mock('react-paginate', () => (props) => (
//   <div data-testid="pagination">
//     <button onClick={() => props.onPageChange({ selected: 1 })}>Next</button>
//   </div>
// ));

// const renderTable = (props = {}) => {
//   const defaultProps = {
//     title: 'Test Table',
//     columnNames: [
//       { selector: 'name', name: 'COLUMN_NAME' },
//       { selector: 'numberAnnotations', name: 'NUMBER_ANNOTATIONS' },
//     ],
//     tableData: [
//       { name: 'Leo', numberAnnotations: 3 },
//       { name: 'Nala', numberAnnotations: 1 },
//     ],
//     totalItems: 2,
//     page: 0,
//     perPage: 10,
//     onPageChange: jest.fn(),
//     onPerPageChange: jest.fn(),
//     setSort: jest.fn(),
//   };
//   const mergedProps = { ...defaultProps, ...props };
//   return renderWithProviders(
//       <MyDataTable {...mergedProps} />
//   );
// };

// describe('MyDataTable', () => {
//   test('renders basic layout and title', () => {
//     renderTable();
//     expect(screen.getByText('Test Table')).toBeInTheDocument();
//     expect(screen.getByTestId('mock-table')).toBeInTheDocument();
//   });

//   // test('renders FormattedMessage IDs', () => {
//   //   renderTable({
//   //     tabs: ['TAB_LABEL:/some/link'],
//   //   });

//   //   expect(screen.getByTestId('intl-RESULTS_TABLE')).toBeInTheDocument();
//   //   expect(screen.getByTestId('intl-TAB_LABEL')).toBeInTheDocument();
//   //   expect(screen.getByTestId('intl-SEARCH')).toBeInTheDocument();
//   //   expect(screen.getByTestId('intl-TOTAL_ITEMS')).toBeInTheDocument();
//   //   expect(screen.getByTestId('intl-PER_PAGE')).toBeInTheDocument();
//   //   expect(screen.getByTestId('intl-GO_TO')).toBeInTheDocument();
//   //   expect(screen.getByTestId('intl-GO')).toBeInTheDocument();
//   // });

//   // test('filters data when typing in search bar', () => {
//   //   renderTable();
//   //   const input = screen.getByPlaceholderText('SEARCH');
//   //   fireEvent.change(input, { target: { value: 'Leo' } });
//   //   expect(input.value).toBe('Leo');
//   // });

//   // test('clears filter when clicking X button', () => {
//   //   renderTable();
//   //   const input = screen.getByPlaceholderText('SEARCH');
//   //   fireEvent.change(input, { target: { value: 'Leo' } });
//   //   const clearBtn = screen.getByRole('button', { name: '' });
//   //   fireEvent.click(clearBtn);
//   //   expect(input.value).toBe('');
//   // });

//   // test('triggers pagination change', () => {
//   //   const onPageChange = jest.fn();
//   //   renderTable({ onPageChange });
//   //   fireEvent.click(screen.getByText('Next'));
//   //   expect(onPageChange).toHaveBeenCalledWith(1);
//   // });

//   // test('calls onRowClicked when row clicked', () => {
//   //   const onRowClicked = jest.fn();
//   //   renderTable({ onRowClicked });
//   //   fireEvent.click(screen.getAllByTestId('row')[0]);
//   //   expect(onRowClicked).toHaveBeenCalled();
//   // });

//   // test('shows no results message if data is empty', () => {
//   //   renderTable({ tableData: [], totalItems: 0 });
//   //   expect(screen.getByTestId('intl-NO_RESULTS_FOUND')).toBeInTheDocument();
//   // });

//   // test('calls onPerPageChange when page size is changed', () => {
//   //   const onPerPageChange = jest.fn();
//   //   renderTable({ onPerPageChange });
//   //   const select = screen.getByDisplayValue('10');
//   //   fireEvent.change(select, { target: { value: '20' } });
//   //   expect(onPerPageChange).toHaveBeenCalledWith(20);
//   // });

//   // test('go to page input updates correctly', () => {
//   //   renderTable();
//   //   const input = screen.getByRole('textbox', { name: '' });
//   //   fireEvent.change(input, { target: { value: '2' } });
//   //   expect(input.value).toBe('2');
//   // });
// });
