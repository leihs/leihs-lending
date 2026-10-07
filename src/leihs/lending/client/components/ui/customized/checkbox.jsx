import * as React from "react"
import { cn } from "cn"
import { Checkbox as BaseCheckbox } from "../checkbox"

// shadcn checkbox with a more visible border when unchecked
function Checkbox({
  className,
  ...props
}) {
  return (
    <BaseCheckbox
      className={cn("border-muted-foreground/60", className)}
      {...props} />
  );
}

export { Checkbox }
