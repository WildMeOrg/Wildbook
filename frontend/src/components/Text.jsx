import React, { forwardRef } from 'react';
import { FormattedMessage } from 'react-intl';

const Core = (props, ref) => {
  const { responsive = false, className, ...rest } = props;
  const Tag = responsive ? 'span' : 'div';
  return <Tag ref={ref} className={`text-${className}`} {...rest} />;
};

const CoreForwardRef = forwardRef(Core);

const Text = ({ id, values, domId, children, className = '', ...rest }, ref) => {
  if (!id)
    return (
      <CoreForwardRef id={domId} ref={ref} className={className} {...rest}>
        {children}
      </CoreForwardRef>
    );
  return (
    <CoreForwardRef id={domId} ref={ref} className={className} {...rest}>
      <FormattedMessage id={id} values={values} defaultMessage={id} />
    </CoreForwardRef>
  );
};

export default forwardRef(Text);
