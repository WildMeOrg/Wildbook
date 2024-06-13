import React, { useState, useEffect } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { InputGroup, Form, Button, Container, Row, Col } from "react-bootstrap";
import axios from "axios";
import "bootstrap/dist/css/bootstrap.min.css";

const columns = [
  { name: "ID", selector: (row) => row.id, sortable: true },
  { name: "Name", selector: (row) => row.name, sortable: true },
  { name: "Age", selector: (row) => row.age, sortable: true },
];

const customStyles = {
  rows: {
    style: {
      border: "none !important",
      borderRadius: "5px",
    },
  },
};

const conditionalRowStyles = [
  {
    when: (row) => row.id % 2 === 0,
    style: {
      backgroundColor: "#ffffff", // Light gray color
    },
  },
  {
    when: (row) => row.id % 2 !== 0,
    style: {
      backgroundColor: "#f2f2f2", // White color
    },
  },
];

const MyDataTable = () => {
  const [data, setData] = useState([]);
  const [totalRows, setTotalRows] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0); // page is zero-based in react-paginate
  const [perPage, setPerPage] = useState(10);
  const [goToPage, setGoToPage] = useState("");

  const fetchData = async (currentPage, perPage) => {
    setLoading(true);
    try {
      // 模拟从服务器获取数据
      const response = {
        data: {
          data: Array.from({ length: perPage }, (_, i) => ({
            id: currentPage * perPage + i + 1,
            name: `John Doe ${currentPage * perPage + i + 1}`,
            age: Math.floor(Math.random() * 100),
          })),
          total: 85, // 假设总共有85条数据
        },
      };
      setData(response.data.data);
      setTotalRows(response.data.total);
    } catch (error) {
      console.error("Error fetching data:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData(page, perPage);
  }, [page, perPage]);

  const handlePageChange = ({ selected }) => {
    setPage(selected);
  };

  const handleGoToPageChange = (event) => {
    setGoToPage(event.target.value);
  };

  const handleGoToPageSubmit = () => {
    const pageNumber = Number(goToPage) - 1; // react-paginate is zero-based
    if (
      !isNaN(pageNumber) &&
      pageNumber >= 0 &&
      pageNumber < Math.ceil(totalRows / perPage)
    ) {
      setPage(pageNumber);
    }
  };

  return (
    <Container>
      <DataTable
        title="Server-Side Pagination"
        columns={columns}
        data={data}
        progressPending={loading}
        customStyles={customStyles}
        conditionalRowStyles={conditionalRowStyles}
      />
      <Row className="mt-3">
        <Col>
          <ReactPaginate
            previousLabel={"<"}
            nextLabel={">"}
            breakLabel={"..."}
            breakClassName={"page-item"}
            breakLinkClassName={"page-link"}
            pageCount={Math.ceil(totalRows / perPage)}
            marginPagesDisplayed={2}
            pageRangeDisplayed={5}
            onPageChange={handlePageChange}
            containerClassName={"pagination"}
            pageClassName={"page-item"}
            pageLinkClassName={"page-link"}
            previousClassName={"page-item"}
            previousLinkClassName={"page-link"}
            nextClassName={"page-item"}
            nextLinkClassName={"page-link"}
            activeClassName={"active"}
            forcePage={page}
          />
        </Col>
        <Col className="text-right">
          <InputGroup style={{ width: "150px" }} className="ml-auto">
            <InputGroup.Text>Go to</InputGroup.Text>
            <Form.Control
              type="number"
              value={goToPage}
              onChange={handleGoToPageChange}
              min="1"
              max={Math.ceil(totalRows / perPage)}
            />
            <Button variant="primary" onClick={handleGoToPageSubmit}>
              Go
            </Button>
          </InputGroup>
        </Col>
      </Row>
    </Container>
  );
};

export default MyDataTable;
