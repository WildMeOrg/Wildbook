import React, { forwardRef } from "react";
import { Link as RouterLink } from "react-router-dom";

function Link(
  {
    children,
    href,
    style,
    disabled = false,
    noUnderline = false,
    external = false,
    newTab = false,
    onClick,
    ...rest
  },
  ref,
) {
  const styles = {
    color: disabled ? "grey" : "unset",
    textDecoration: noUnderline ? "unset" : "underline",
    cursor: disabled ? "default" : "pointer",
    ...style,
  };

  if (disabled) {
    return (
      <div style={styles} ref={ref} {...rest}>
        {children}
      </div>
    );
  }

  if (external) {
    return (
      <a
        href={href}
        target={newTab ? "_blank" : undefined}
        rel={newTab ? "noreferrer" : undefined}
        style={styles}
        onClick={onClick}
        ref={ref}
        {...rest}
      >
        {children}
      </a>
    );
  }

  return (
    <RouterLink
      to={href}
      style={styles}
      onClick={onClick}
      target={newTab ? "_blank" : undefined}
      rel={newTab ? "noreferrer" : undefined}
      ref={ref}
      {...rest}
    >
      {children}
    </RouterLink>
  );
}

export default forwardRef(Link);
