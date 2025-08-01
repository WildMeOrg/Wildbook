(function (global, factory) {
  if (typeof define === "function" && define.amd) {
    define([], factory);
  } else if (typeof exports !== "undefined") {
    factory();
  } else {
    var mod = {
      exports: {}
    };
    factory();
    global.bootstrapTableCsCZ = mod.exports;
  }
})(this, function () {
  'use strict';

  /**
   * Bootstrap Table Czech translation
   * Author: Lukas Kral (monarcha@seznam.cz)
   * Author: Jakub Svestka <svestka1999@gmail.com>
   */
  (function ($) {
    $.fn.bootstrapTable.locales['cs-CZ'] = {
      formatLoadingMessage: function formatLoadingMessage() {
        return 'Čekejte, prosím';
      },
      formatRecordsPerPage: function formatRecordsPerPage(pageNumber) {
        return pageNumber + ' polo\u017Eek na str\xE1nku';
      },
      formatShowingRows: function formatShowingRows(pageFrom, pageTo, totalRows) {
        return 'Zobrazena ' + pageFrom + '. - ' + pageTo + '. polo\u017Eka z celkov\xFDch ' + totalRows;
      },
      formatDetailPagination: function formatDetailPagination(totalRows) {
        return 'Showing ' + totalRows + ' rows';
      },
      formatSearch: function formatSearch() {
        return 'Vyhledávání';
      },
      formatNoMatches: function formatNoMatches() {
        return 'Nenalezena žádná vyhovující položka';
      },
      formatPaginationSwitch: function formatPaginationSwitch() {
        return 'Skrýt/Zobrazit stránkování';
      },
      formatRefresh: function formatRefresh() {
        return 'Aktualizovat';
      },
      formatToggle: function formatToggle() {
        return 'Přepni';
      },
      formatColumns: function formatColumns() {
        return 'Sloupce';
      },
      formatFullscreen: function formatFullscreen() {
        return 'Fullscreen';
      },
      formatAllRows: function formatAllRows() {
        return 'Vše';
      },
      formatAutoRefresh: function formatAutoRefresh() {
        return 'Auto Refresh';
      },
      formatExport: function formatExport() {
        return 'Export data';
      },
      formatClearFilters: function formatClearFilters() {
        return 'Clear filters';
      },
      formatJumpto: function formatJumpto() {
        return 'GO';
      },
      formatAdvancedSearch: function formatAdvancedSearch() {
        return 'Advanced search';
      },
      formatAdvancedCloseButton: function formatAdvancedCloseButton() {
        return 'Close';
      }
    };

    $.extend($.fn.bootstrapTable.defaults, $.fn.bootstrapTable.locales['cs-CZ']);
  })(jQuery);
});