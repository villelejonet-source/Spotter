import { describe, expect, it } from "vitest";
import { cardClass } from "./ui";

describe("cardClass", () => {
  it("pads a card by default, less on phones", () => {
    expect(cardClass()).toBe("panel relative rounded-2xl p-4 sm:p-5");
    expect(cardClass("mt-4 lg:col-span-2")).toBe("panel relative rounded-2xl p-4 sm:p-5 mt-4 lg:col-span-2");
  });

  it("drops the default when the card sets its own all-sides padding", () => {
    expect(cardClass("p-0")).toBe("panel relative rounded-2xl p-0");
    expect(cardClass("p-2 sm:p-4")).toBe("panel relative rounded-2xl p-2 sm:p-4");
    expect(cardClass("mt-4 p-6 sm:p-8")).toBe("panel relative rounded-2xl mt-4 p-6 sm:p-8");
    expect(cardClass("sm:p-4")).toBe("panel relative rounded-2xl sm:p-4");
  });

  it("keeps the default when the card only pads one axis", () => {
    expect(cardClass("py-12 text-center")).toBe("panel relative rounded-2xl p-4 sm:p-5 py-12 text-center");
    expect(cardClass("px-4 top-0")).toBe("panel relative rounded-2xl p-4 sm:p-5 px-4 top-0");
  });
});
