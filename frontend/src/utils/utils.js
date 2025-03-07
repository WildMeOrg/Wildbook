import React from 'react';
import { render, fireEvent, waitFor, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import axios from 'axios';
import { QueryClient, QueryClientProvider } from 'react-query';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router-dom';
import messages from '../locale/en.json';

jest.mock('axios'); 

const renderWithProviders = (ui) => {
    const queryClient = new QueryClient();
    return render(
      <QueryClientProvider client={queryClient}>
        <IntlProvider locale="en" messages={messages}>
          <MemoryRouter>{ui}</MemoryRouter>
        </IntlProvider>
      </QueryClientProvider>
    );
  };  

const renderWithRouter = (ui, { route = '/' } = {}) => {
  window.history.pushState({}, 'Test page', route);
  return render(ui, { wrapper: BrowserRouter });
};

const fireInput = (placeholder, value) => {
  const input = screen.getByPlaceholderText(placeholder);
  fireEvent.change(input, { target: { value } });
  return input; 
};

const clickButton = (buttonText) => {
  fireEvent.click(screen.getByRole('button', { name: buttonText }));
};

const waitForText = async (text) => {
  await waitFor(() => expect(screen.getByText(text)).toBeInTheDocument());
};

const mockAxiosSuccess = (mockedData) => {
  axios.get.mockResolvedValue({ data: mockedData });
};

const mockAxiosFailure = () => {
  axios.get.mockRejectedValue(new Error('Network error'));
};

export * from '@testing-library/react';
export {
  renderWithRouter as render,
  fireInput,
  clickButton,
  waitForText,
  mockAxiosSuccess,
  mockAxiosFailure,
  renderWithProviders,
};
