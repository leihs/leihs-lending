import * as React from "react"
import { cn } from "cn"
import { Checkbox as BaseCheckbox } from "../checkbox"

// shadcn checkbox with a foreground-colored border also when unchecked
function Checkbox({
  className,
  ...props
}) {
  return (
    <BaseCheckbox
      className={cn("border-foreground", className)}
      {...props} />
  );
}

export { Checkbox }
