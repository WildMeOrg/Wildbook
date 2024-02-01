/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

scheduler.attachEvent("onTemplatesReady", function() {
  scheduler.attachEvent("onBeforeLightbox", function(F) {
    for (var E = 0; E < this.config.lightbox.sections.length; E++) {
      this.config.lightbox.sections[E].focus = false
    }
    if (this.config.readonly_form || this.getEvent(F).readonly) {
      this.config.readonly_active = true
    } else {
      this.config.readonly_active = false
    }
    return true
  });
  function D(E, K, L, J) {
    var G = K.getElementsByTagName(E);
    var F = L.getElementsByTagName(E);
    for (var I = F.length - 1; I >= 0; I--) {
      var H = document.createElement("SPAN");
      H.className = "dhx_text_disabled";
      H.innerHTML = J(G[I]);
      F[I].parentNode.insertBefore(H, F[I]);
      F[I].parentNode.removeChild(F[I])
    }
  }

  var B = scheduler._fill_lightbox;
  scheduler._fill_lightbox = function() {
    var H = this.config.lightbox.sections;
    if (this.config.readonly_active) {
      for (var F = 0; F < H.length; F++) {
        if (H[F].type == "recurring") {
          var G = document.getElementById(H[F].id);
          G.style.display = G.nextSibling.style.display = "none";
          H.splice(F, 1);
          F--
        }
      }
    }
    var E = B.apply(this, arguments);
    if (this.config.readonly_active) {
      var I = this._get_lightbox();
      var J = this._lightbox_r = I.cloneNode(true);
      D("textarea", I, J, function(K) {
        return K.value
      });
      D("select", I, J, function(K) {
        return K.options[K.selectedIndex || 0].text
      });
      J.removeChild(J.childNodes[2]);
      J.removeChild(J.childNodes[3]);
      I.parentNode.insertBefore(J, I);
      A.call(this, J);
      this._lightbox = J;
      this.setLightboxSize();
      this._lightbox = null;
      J.onclick = function(K) {
        var L = K ? K.target : event.srcElement;
        if (!L.className) {
          L = L.previousSibling
        }
        if (L && L.className) {
          switch (L.className) {case"dhx_cancel_btn":scheduler.callEvent("onEventCancel", [scheduler._lightbox_id]);scheduler._edit_stop_event(scheduler.getEvent(scheduler._lightbox_id), false);scheduler.hide_lightbox();break
          }
        }
      }
    }
    return E
  };
  var A = scheduler.showCover;
  scheduler.showCover = function() {
    if (!this.config.readonly_active) {
      A.apply(this, arguments)
    }
  };
  var C = scheduler.hide_lightbox;
  scheduler.hide_lightbox = function() {
    if (this._lightbox_r) {
      this._lightbox_r.parentNode.removeChild(this._lightbox_r);
      this._lightbox_r = null
    }
    return C.apply(this, arguments)
  }
});