<jsp:include page="header.jsp" flush="true"/>

<script type="text/javascript">
    setTimeout(function () {
        window.location.href = "task";
    }, 3500);
</script>

<style>
    .spinner {
        width: 50px;
        height: 50px;
        border: 6px solid #ddd;
        border-top: 6px solid #3498db;
        border-radius: 50%;
        animation: spin 1s linear infinite;
        margin-bottom: 20px;
    }

    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }
        100% {
            transform: rotate(360deg);
        }
    }
</style>

<div class="container maincontent">
    <h2>Retrying Failed Task</h2>
    <div class="spinner"></div>
</div>

<jsp:include page="footer.jsp" flush="true"/>
